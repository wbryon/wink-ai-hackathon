import axios from 'axios';
import { mockAPI } from '../utils/mockData';

// Базовый URL для API
// В production (Docker) используем относительный путь через vite proxy
// В development можно использовать переменную окружения или относительный путь
const getApiBaseUrl = () => {
  // Если есть переменная окружения, используем её (для локальной разработки)
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  // В production используем относительный путь (работает через vite proxy в dev и напрямую в prod)
  return '/api';
};

const API_BASE_URL = getApiBaseUrl();
const USE_MOCKS = (import.meta?.env?.VITE_USE_MOCKS === 'true') || (typeof window !== 'undefined' && window.USE_MOCKS === true);

// Создаем экземпляр axios с базовыми настройками
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  // НЕ устанавливаем Content-Type по умолчанию, чтобы axios мог правильно установить его для FormData
  timeout: 120000, // 2 минуты для генерации изображений
});

// Интерцептор для установки Content-Type только для JSON запросов
apiClient.interceptors.request.use(
  (config) => {
    // Если это не FormData и Content-Type не установлен, устанавливаем application/json
    if (!(config.data instanceof FormData) && !config.headers['Content-Type']) {
      config.headers['Content-Type'] = 'application/json';
    }
    // Для FormData не устанавливаем Content-Type - браузер сам установит правильный boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Интерцептор для обработки ошибок
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Детальное логирование ошибок
    if (error.response) {
      // Сервер ответил с ошибкой
      console.error('API Error Response:', {
        status: error.response.status,
        statusText: error.response.statusText,
        data: error.response.data,
        url: error.config?.url,
      });
    } else if (error.request) {
      // Запрос отправлен, но ответа нет
      console.error('API Error: No response received', {
        url: error.config?.url,
        method: error.config?.method,
      });
    } else {
      // Ошибка при настройке запроса
      console.error('API Error: Request setup failed', error.message);
    }
    return Promise.reject(error);
  }
);

const maybeFallback = async (realCall, mockCall) => {
  try {
    return await realCall();
  } catch (e) {
    if (USE_MOCKS && typeof mockCall === 'function') {
      console.warn('Falling back to mock API due to error:', e?.message || e);
      return await mockCall();
    }
    throw e;
  }
};

/**
 * Загрузка файла сценария (PDF/DOCX)
 * @param {File} file - Файл сценария
 * @returns {Promise} - Промис с ID сценария
 */
export const uploadScript = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  return maybeFallback(
    async () => {
      // Axios автоматически установит правильный Content-Type с boundary для FormData
      // Интерцептор уже обработает удаление Content-Type для FormData
      const response = await apiClient.post('/scripts/upload', formData, {
        timeout: 300000, // 5 минут для больших файлов
      });
      return response.data;
    },
    async () => mockAPI.uploadScript(file)
  );
};

/**
 * Получение списка сцен из обработанного сценария
 * @param {string} scriptId - ID сценария
 * @returns {Promise} - Промис со списком сцен
 */
export const getScenes = async (scriptId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/scripts/${scriptId}/scenes`)).data,
    async () => mockAPI.getScenes(scriptId)
  );
};

/**
 * Обновление информации о сцене
 * @param {string} sceneId - ID сцены
 * @param {Object} sceneData - Обновленные данные сцены
 * @returns {Promise} - Промис с обновленной сценой
 */
export const updateScene = async (sceneId, sceneData) => {
  return maybeFallback(
    async () => (await apiClient.put(`/scenes/${sceneId}`, sceneData)).data,
    async () => mockAPI.updateScene(sceneId, sceneData)
  );
};

/**
 * Генерация изображения для сцены
 * @param {string} sceneId - ID сцены
 * @param {string} detailLevel - Уровень детализации ('sketch', 'mid', 'final', 'direct_final')
 * @param {string} path - Путь генерации ('progressive' или 'direct', опционально)
 * @param {Object} options - Дополнительные опции (prompt, seed, model)
 * @returns {Promise} - Промис с URL сгенерированного изображения
 */
export const generateFrame = async (sceneId, detailLevel = 'sketch', path = null, options = {}) => {
  const requestBody = {
    detailLevel,
    ...(path && { path }),
    ...(options.prompt && { prompt: options.prompt }),
    ...(options.seed !== undefined && { seed: options.seed }),
    ...(options.model && { model: options.model }),
  };

  return maybeFallback(
    async () => (await apiClient.post(`/scenes/${sceneId}/generate`, requestBody)).data,
    async () => mockAPI.generateFrame(sceneId, detailLevel)
  );
};

/**
 * Генерация кадра через progressive path: Sketch → Mid → Final
 * @param {string} sceneId - ID сцены
 * @param {string} targetLod - Целевой LOD ('mid' или 'final', по умолчанию 'final')
 * @param {Object} options - Дополнительные опции (prompt, seed, model)
 * @returns {Promise} - Промис с финальным кадром
 */
export const generateProgressiveFrame = async (sceneId, targetLod = 'final', options = {}) => {
  const requestBody = {
    detailLevel: targetLod,
    ...(options.prompt && { prompt: options.prompt }),
    ...(options.seed !== undefined && { seed: options.seed }),
    ...(options.model && { model: options.model }),
  };

  return maybeFallback(
    async () => (await apiClient.post(`/scenes/${sceneId}/generate-progressive`, requestBody)).data,
    async () => mockAPI.generateFrame(sceneId, targetLod)
  );
};

/**
 * Регенерация изображения с новым промптом
 * @param {string} frameId - ID кадра
 * @param {string} prompt - Новый промпт для генерации
 * @param {string} detailLevel - Уровень детализации ('sketch', 'mid', 'final', 'direct_final')
 * @param {string} path - Путь генерации ('progressive' или 'direct', опционально)
 * @returns {Promise} - Промис с URL нового изображения
 */
export const regenerateFrame = async (frameId, prompt, detailLevel, path = null) => {
  const requestBody = {
    prompt,
    detailLevel,
    ...(path && { path }),
  };

  return maybeFallback(
    async () => (await apiClient.post(`/frames/${frameId}/regenerate`, requestBody)).data,
    async () => mockAPI.regenerateFrame(frameId, prompt, detailLevel)
  );
};

/**
 * Получение истории генераций для сцены
 * @param {string} sceneId - ID сцены
 * @returns {Promise} - Промис с историей генераций
 */
export const getFrameHistory = async (sceneId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/scenes/${sceneId}/frames`)).data,
    async () => mockAPI.getFrameHistory(sceneId)
  );
};

/**
 * Экспорт storyboard в PDF
 * @param {string} scriptId - ID сценария
 * @returns {Promise} - Промис с blob PDF файла
 */
export const exportStoryboard = async (scriptId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/scripts/${scriptId}/export`, { responseType: 'blob' })).data,
    async () => mockAPI.exportStoryboard(scriptId)
  );
};

/**
 * Получение frame-card (карточек кадров) для сценария
 * @param {string} scriptId - ID сценария
 * @returns {Promise} - Промис со списком frame-card
 */
export const getFrameCards = async (scriptId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/scripts/${scriptId}/frames/cards`)).data
  );
};

/**
 * Получение enriched JSON, flux-промпта и слотов для сцены
 * @param {string} sceneId - ID сцены
 * @returns {Promise} - SceneVisualDto
 */
export const getSceneVisual = async (sceneId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/visual/scenes/${sceneId}`)).data
  );
};

/**
 * Получение промпт-слотов для сцены
 * @param {string} sceneId - ID сцены
 * @returns {Promise} - PromptSlotsDto
 */
export const getSlots = async (sceneId) => {
  return maybeFallback(
    async () => (await apiClient.get(`/scenes/${sceneId}/slots`)).data,
    async () => null // Mock не нужен, так как это новый функционал
  );
};

/**
 * Обновление промпт-слотов для сцены
 * @param {string} sceneId - ID сцены
 * @param {Object} slots - Обновленные слоты (PromptSlotsDto)
 * @param {boolean} updateScene - Обновлять ли связанную сущность Scene (по умолчанию false)
 * @returns {Promise} - SceneVisualDto
 */
export const updateSlots = async (sceneId, slots, updateScene = false) => {
  return maybeFallback(
    async () => (await apiClient.put(`/scenes/${sceneId}/slots?updateScene=${updateScene}`, slots)).data,
    async () => null // Mock не нужен
  );
};

/**
 * Удаление сцены
 * @param {string} sceneId - ID сцены
 * @returns {Promise}
 */
export const deleteScene = async (sceneId) => {
  return maybeFallback(
    async () => (await apiClient.delete(`/scenes/${sceneId}`)).data,
    async () => mockAPI.deleteScene(sceneId)
  );
};

/**
 * Добавление новой сцены
 * @param {string} scriptId - ID сценария
 * @param {Object} sceneData - Данные новой сцены
 * @returns {Promise} - Промис с новой сценой
 */
export const addScene = async (scriptId, sceneData) => {
  return maybeFallback(
    async () => (await apiClient.post(`/scripts/${scriptId}/scenes`, sceneData)).data,
    async () => mockAPI.addScene(scriptId, sceneData)
  );
};

/**
 * Быстрая правка сцены коротким текстом
 * @param {string} sceneId
 * @param {string} instruction - текст правки от пользователя
 */
export const refineScene = async (sceneId, instruction) => {
  return maybeFallback(
    async () => (await apiClient.post(`/scenes/${sceneId}/refine`, { instruction })).data
  );
};

/**
 * Загрузка workflow файла на сервер
 * @param {string} type - Тип workflow ('text2img' или 'img2img')
 * @param {File|Blob} file - Файл workflow (JSON)
 * @returns {Promise} - Промис с результатом загрузки
 */
export const uploadWorkflow = async (type, file) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('type', type);

  return maybeFallback(
    async () => {
      const response = await apiClient.post('/workflows/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return response.data;
    },
    async () => ({ success: false, error: 'Mock not implemented' })
  );
};

/**
 * Получение информации о workflow файлах
 * @returns {Promise} - Промис с информацией о workflow
 */
export const getWorkflowsInfo = async () => {
  return maybeFallback(
    async () => (await apiClient.get('/workflows/info')).data,
    async () => ({ success: false, error: 'Mock not implemented' })
  );
};

export default apiClient;

