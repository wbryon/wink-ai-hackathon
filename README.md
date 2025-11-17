# Wink PreViz – локальная платформа превизуализации сценариев

Wink PreViz – это self‑hosted веб‑платформа для режиссёров и художников‑постановщиков, которая:

- принимает сценарий (DOCX/PDF),
- автоматически разбивает его на сцены и извлекает структуру (локации, персонажи, реквизит, тон),
- строит управляемый промпт на основе структурированного JSON,
- генерирует статические кадры storyboard через локальный пайплайн ComfyUI,
- даёт возможность править сцены, промпты и перегенерировать кадры,
- работает **полностью офлайн** на одной машине (Docker Compose).

---

## Архитектура системы (high‑level)

### Сервисы

- **frontend/** – React + Vite + Nginx  
  UI с тремя основными шагами:
  1. Загрузка сценария
  2. Проверка и правка сцен
  3. Генерация и просмотр кадров

- **backend/** – Spring Boot 3 (Java 17)  
  Основной API и оркестрация:
  - управление сценариями и сценами (PostgreSQL),
  - постановка задач парсинга сцен в очередь,
  - запуск LLM‑пайплайна обогащения (base JSON → enriched JSON → Flux prompt),
  - вызовы сервиса генерации изображений (ai‑modules),
  - раздача метаданных и изображений фронтенду.

- **script_processor/** – FastAPI (Python)  
  Разбивает загруженный DOCX/PDF на текстовые чанки, нормализует текст, возвращает JSON с чанками.

- **ai-modules/** – FastAPI (Python) + ComfyUI  
  Сервис генерации изображений:
  - `/generate` – text2img через ComfyUI workflow на базе **Flux Schnell** по заданному LOD (sketch/mid/final/direct_final),
  - `/img2img` – прогрессивный путь (Sketch → Mid → Final),
  - управление workflow’ами ComfyUI в формате **Save (API Format)**,
  - сохранение картинок в общий каталог `/data/frames` и возврат относительного URL `/api/images/<file>.png`.

- **PostgreSQL** – хранение:
  - `scripts` – сценарий целиком,
  - `scenes` – отдельные сцены с текстом, локациями, персонажами, реквизитом, статусами парсинга,
  - `frames` – сгенерированные кадры (LOD, путь, prompt, seed, meta),
  - `scene_visuals` – enriched JSON и Flux‑промпт для сцен.

- **Ollama (на хосте)** – локальный LLM сервер  
  Используется в Java‑сервисе (через `OllamaClient`) с моделью **`qwen3:4b-instruct`** для:
  - парсинга текста сцены → base JSON,
  - обогащения base JSON → enriched JSON,
  - построения Flux‑промпта для визуализации.

### Основной поток данных

```text
1. Upload Script (DOCX/PDF) на frontend
   → backend сохраняет файл в /data/uploads
   → вызывает script_processor /split-script

2. script_processor:
   DOCX/PDF → чистый текст → чанки → JSON
   → backend получает список чанков

3. Scene parsing (backend):
   - создаёт сущности Scene в БД
   - ставит задачи в очередь парсинга сцен
   - SceneParsingWorkerService по одной сцене:
       scene.description
       → OllamaScriptParserService.parseSceneTextToJson()
       → base JSON (originalJson) + обновление полей Scene

4. Пользователь на шаге "2. Сцены":
   - видит список сцен и текст
   - может открыть 0. Base JSON (результат парсинга) для каждой сцены

5. Enrichment pipeline (по кнопке "Запустить пайплайн обогащения"):
   base JSON
   → SceneToFluxPromptService.enhanceScene() (LLM Enricher)
   → enriched JSON
   → SceneToFluxPromptService.buildFluxPrompt() (LLM Prompt Builder)
   → Flux prompt
   → SceneVisualEntity (enrichedJson + fluxPrompt, status= PROMPT_READY)
   → фронт показывает:
       1. Enriched JSON
       2. Flux Prompt

6. Generate Frame (шаг "3. Кадры"):
   - backend.getOrCreatePrompt():
       использует сохранённый Flux prompt из SceneVisualEntity
       или при отсутствии запускает enrichment pipeline на лету
   - AiImageClient.generateWithProfile():
       вызывает ai-modules /generate (LOD-профиль: sketch/mid/final/direct_final)
       → ai-modules через ComfyUI создаёт кадр, кладёт в /data/frames
       → возвращает image_url вида /api/images/frame_xxx.png
   - backend создаёт Frame, сохраняет meta (LOD, seed, cfg, steps, resolution)

7. Frontend:
   - загружает список сцен + history /frames/{sceneId}
   - отображает текущий кадр в окне просмотра
   - отдельный контроллер backend `/api/images/{filename}` отдаёт картинку
```

---

## Структура репозитория

```text
wink-ai-previz/
├── backend/                 # Spring Boot API и бизнес-логика
│   ├── src/main/java/ru/wink/winkaipreviz/
│   │   ├── controller/      # REST API (scripts, scenes, frames, images)
│   │   ├── service/         # PrevizService, SceneToFluxPromptService, OllamaScriptParserService и др.
│   │   ├── entity/          # Script, Scene, Frame, SceneVisualEntity и т.п.
│   │   └── repository/      # JPA-репозитории (PostgreSQL)
│   ├── src/main/resources/
│   │   ├── application.yml  # конфигурация сервисов (AI, Ollama, script-processor)
│   │   └── db/migration/    # Flyway миграции схемы БД
│   └── README.md
│
├── frontend/                # React + Vite + Nginx UI
│   ├── src/
│   │   ├── pages/MainPage.jsx       # шаги 1–3
│   │   ├── components/UploadScene.jsx
│   │   ├── components/SceneList.jsx
│   │   ├── components/FrameViewer.jsx
│   │   └── api/apiClient.js         # клиент к backend API
│   ├── nginx.conf                   # proxy /api → backend, /comfyui → ComfyUI
│   └── README.md
│
├── ai-modules/             # FastAPI сервис над ComfyUI
│   ├── main.py             # /generate, /img2img, /health, /images
│   ├── comfy_client.py     # POST /prompt, GET /history, /view, /upload/image
│   ├── image_generator.py  # LOD профили, сохранение кадров
│   ├── config.py           # COMFY_BASE_URL, TEXT2IMG_WORKFLOW_PATH, IMG2IMG_WORKFLOW_PATH
│   └── README.md
│
├── script_processor/       # Парсер сценариев (Python FastAPI)
│   ├── api.py              # /split-script
│   ├── docx_scene_parser.py
│   ├── pdf_utils.py
│   └── ...
│
├── docker-compose.yml      # общий docker-compose для db, backend, frontend, ai, script_processor
├── README_DOCKER.md        # подробности docker-развёртывания
├── ARCHITECTURE_ANALYSIS.md# детальный архитектурный разбор и рекомендации
└── data/
    ├── uploads/            # загруженные сценарии
    └── frames/             # сгенерированные изображения (общий volume backend/ai)
```

---

## Запуск через Docker Compose (рекомендуется)

Полная инструкция в [`README_DOCKER.md`](README_DOCKER.md). Ниже – краткая схема с учётом скрипта `start_wink.sh`.

### Вариант 1. Автоматический запуск через `start_wink.sh` (рекомендуется на сервере с динамическим IP)

Скрипт делает три вещи:

1. Определяет текущий внешний IP сервера (через `api.ipify.org`, `ifconfig.me`, `icanhazip.com`).
2. Обновляет/создаёт `.env` в корне проекта, прописывая `SERVER_IP=<текущий IP>` – это значение использует `docker-compose.yml` (например, для CORS).
3. Полностью пересобирает и поднимает контейнеры **без кеша**.

Команды:

```bash
git clone <repository-url>
cd wink-ai-previz

chmod +x start_wink.sh          # один раз, чтобы скрипт был исполняемым
./start_wink.sh                 # или: bash start_wink.sh
```

После успешного выполнения:

- Frontend: `http://<SERVER_IP>:3000`
- Backend API: `http://<SERVER_IP>:8080/api`

Скрипт удобно запускать каждый раз после смены IP или после обновления кода.

### Вариант 2. Ручной запуск Docker Compose (локальная разработка)

```bash
git clone <repository-url>
cd wink-ai-previz

# (опционально) создать .env из примера
cp .env.example .env   # если файл существует

docker compose up --build
```

В этом случае сервисы будут доступны по `localhost`:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api`
- Swagger (backend): `http://localhost:8080/api/v1/swagger-ui.html`
- AI‑modules: `http://localhost:8000`
- Script Processor: `http://localhost:8020`

> **Важно:** ComfyUI должен быть запущен на хосте (обычно `http://127.0.0.1:8188`)  
> и доступен из контейнера `ai` через `COMFY_BASE_URL` (по умолчанию `http://host.docker.internal:8188`).

---

## Установка и настройка Ollama + Qwen3:4B Instruct

Для парсинга текста сцен и генерации промптов backend использует **Ollama** с моделью `qwen3:4b-instruct`.

### 1. Установка Ollama

Следуйте официальной инструкции `https://ollama.com/download` для вашей ОС.  
На Linux, как правило:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Убедитесь, что команда `ollama` доступна:

```bash
ollama --version
```

### 2. Настройка адреса Ollama (OLLAMA_HOST)

Backend и script_processor ожидают, что Ollama слушает только на `127.0.0.1:11434`.  
Для этого на сервере нужно добавить override‑конфиг для systemd‑сервиса Ollama:

```bash
sudo mkdir -p /etc/systemd/system/ollama.service.d

printf '[Service]\nEnvironment="OLLAMA_HOST=127.0.0.1:11434"\n' | \
  sudo tee /etc/systemd/system/ollama.service.d/override.conf

sudo systemctl daemon-reload
sudo systemctl restart ollama
```

После перезапуска Ollama должен слушать на `127.0.0.1:11434`.

### 3. Загрузка модели Qwen3:4B Instruct

```bash
ollama pull qwen3:4b-instruct
```

В `backend/src/main/resources/application.yml` модель задаётся через:

```yaml
ollama:
  base-url: ${OLLAMA_BASE_URL:http://host.docker.internal:11434}
  model: ${OLLAMA_MODEL:qwen3:4b-instruct}
```

Если запускаете всё на одной машине через Docker Compose, `OLLAMA_BASE_URL` по умолчанию указывает на `host.docker.internal:11434`, который проброшен в контейнеры (`extra_hosts` в docker-compose.yml).

---

## Установка и настройка ComfyUI + Flux Schnell

Для генерации кадров используется **ComfyUI** с workflow на основе **Flux Schnell**.

### 1. Установка ComfyUI

Рекомендуемый способ – по официальной инструкции:  
`https://github.com/comfyanonymous/ComfyUI`

Минимально на сервере:

```bash
git clone https://github.com/comfyanonymous/ComfyUI.git
cd ComfyUI
pip install -r requirements.txt
python main.py --listen 0.0.0.0 --port 8188
```

> В продакшене стоит закрепить запуск ComfyUI как systemd‑сервиса; важно, чтобы он был доступен по `http://127.0.0.1:8188` или `http://host.docker.internal:8188` из контейнера `ai`.

### 2. Установка модели Flux Schnell

Скачайте Flux Schnell и подключите её в ComfyUI (через CheckpointLoader / соответствующий узел).  
Конкретное имя файла и путь зависят от вашей сборки, но workflow в `ai-modules/workflows/flux_schnell.json` предполагает, что модель уже настроена в ComfyUI.

### 3. Экспорт workflow в формате "Save (API Format)"

В ComfyUI:

1. Настройте граф text2img/flux так, как требуется.
2. В меню выберите **Save → Save (API Format)**.
3. Сохраните JSON‑файл в локальную папку и замените
   `ai-modules/workflows/flux_schnell.json` (или укажите путь через переменную окружения `WORKFLOWS_DIR`).

Аналогично можно настроить отдельный workflow для img2img (Mid/Final).

### 4. Переменные окружения для ai‑modules

В `docker-compose.yml`:

```yaml
ai:
  environment:
    - COMFY_BASE_URL=${COMFY_BASE_URL:-http://host.docker.internal:8188}
    - STORAGE_DIR=/data/frames
    - IMAGE_URL_BASE=/api/images
    - WORKFLOWS_DIR=/app/workflows
```

`COMFY_BASE_URL` должен указывать на адрес ComfyUI, доступный из контейнера `ai`.

---

## Основные REST API (backend)

### Сценарий и сцены

- `POST /api/scripts/upload` – загрузка DOCX/PDF, запуск фона парсинга
- `GET  /api/scripts/{scriptId}/status` – статус парсинга сценария (кол-во сцен, прогресс)
- `GET  /api/scripts/{scriptId}/scenes` – список сцен (`SceneDto`)
- `GET  /api/scripts/scenes/{sceneId}` – детали сцены + `originalJson` (`SceneDetailsDto`)
- `PUT  /api/scenes/{sceneId}` – обновление сцены (title, location, characters, props, description)
- `POST /api/scenes/{sceneId}/refine` – быстрая правка текста сцены
- `DELETE /api/scenes/{sceneId}` – удалить сцену

### Пайплайн обогащения и визуализация

- `POST /api/scenes/{sceneId}/enrich` – запустить LLM‑пайплайн:  
  scene text → base JSON → enriched JSON → Flux prompt  
  Сохраняет `SceneVisualEntity` и выставляет статус `PROMPT_READY`.

- `GET  /api/visual/scenes/{sceneId}` – получить `SceneVisualDto`:
  - enriched JSON,
  - Flux prompt,
  - промпт‑слоты.

### Генерация кадров

- `POST /api/scenes/{sceneId}/generate` – сгенерировать кадр:
  - `detailLevel`: `sketch | mid | final | direct_final`
  - `path`: `progressive | direct`
  - опционально: `prompt`, `seed`, `model`

- `POST /api/scenes/{sceneId}/generate-progressive` – прогрессивный путь (Sketch → Mid → Final)
- `POST /api/frames/{frameId}/regenerate` – регенерация кадра с новым промптом
- `GET  /api/scenes/{sceneId}/frames` – история кадров для сцены (`FrameDto[]`)
- `GET  /api/images/{filename}` – раздача PNG‑файлов из `/data/frames`

---

## Важные детали реализации (анализ)

### Парсинг сцены (OllamaScriptParserService)

- LLM иногда возвращает:
  - один объект `{...}`,
  - массив `[{...}, ...]`,
  - или JSON с обрезанными скобками.
- Сервис:
  - чистит ответ (`cleanModelOutput`),
  - пытается разобрать **верхнеуровневый** объект/массив,
  - если JSON обрезан (несбалансированные `{}`/`[]`), делает несколько попыток с увеличением `num_predict`,
  - в конце оборачивает объект в массив и возвращает **первую сцену** как `baseJson` (`originalJson`).

### Enrichment pipeline (SceneToFluxPromptService)

- Шаг 1: `enhanceScene(sceneJson)` – обогащение base JSON:
  - несколько попыток с `num_predict` 2000/3000/4000,
  - проверки длины, баланса скобок, диагностические логи,
  - на выходе получается enriched JSON c:
    - нормализованными локациями,
    - персонажами с ролями,
    - камерами, светом, mood и т.п.

- Шаг 2: `buildFluxPrompt(enrichedJson)` – генерация текстового промпта для Flux:
  - выделяет важные поля (описание локации, персонажи, камера, свет),
  - формирует управляемый длинный промпт,
  - логирует первые/последние символы для отладки.

### Генерация изображения (AiImageClient + ai-modules)

- `AiImageClient.generateWithProfile`:
  - выбирает LOD профиль (сколько шагов, CFG, разрешение, негативные промпты),
  - делает HTTP‑вызов к `ai-modules /generate`,
  - оборачивает ответ в `ImageResult` (imageUrl, model, seed, metaJson).

- `ai-modules/image_generator.py`:
  - подбирает seed (если не задан – случайный),
  - через `comfy_client.run_text2img` ставит джобу в ComfyUI (`/prompt` + `/history`),
  - сохраняет PNG в `/data/frames/frame_xxx.png`,
  - возвращает meta (steps, cfg, sampler, resolution).

### Отображение на фронтенде

- **Шаг 2 (SceneList)**:
  - после загрузки файла и завершения парсинга:
    - подгружает `originalJson` для каждой сцены,
    - показывает блок «0. Base JSON (результат парсинга)»,
  - после запуска enrichment pipeline:
    - отображает «1. Enriched JSON» и «2. Flux Prompt».

- **Шаг 3 (FrameViewer)**:
  - при генерации:
    - сразу обновляет `currentScene.currentFrame` локально,
    - дополнительно перезапрашивает список сцен из backend,
  - выбирает кадр для отображения как `effectiveCurrentFrame`:
    - `currentScene.currentFrame` или, если он ещё не заполнен,
    - последний кадр из истории `frameHistory[0]`,
  - грузит сам PNG через `/api/images/{filename}`.

---

## Где читать дальше

- Детальный архитектурный разбор и рекомендации по эволюции системы:  
  [`ARCHITECTURE_ANALYSIS.md`](ARCHITECTURE_ANALYSIS.md)
- Руководство по Docker‑запуску и troubleshooting:  
  [`README_DOCKER.md`](README_DOCKER.md)
- Подробности по backend, frontend и ai‑modules:  
  - [`backend/README.md`](backend/README.md)  
  - [`frontend/README.md`](frontend/README.md)  
  - [`ai-modules/README.md`](ai-modules/README.md)

Этот README даёт обзор всей системы и того, как компоненты связаны между собой; остальные файлы документации углубляются в конкретные сервисы и сценарии эксплуатации.


