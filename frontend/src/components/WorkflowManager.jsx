import { useState, useEffect } from 'react';
import {
  Settings,
  Upload,
  Download,
  CheckCircle2,
  AlertCircle,
  Loader2,
  FileJson,
  RefreshCw,
  Eye,
  Save,
  X,
  Info,
} from 'lucide-react';
import { uploadWorkflow, getWorkflowsInfo } from '../api/apiClient';

/**
 * Компонент для управления ComfyUI workflow файлами через фронтенд.
 * Позволяет:
 * - Загружать workflow файлы
 * - Проверять их валидность
 * - Просматривать структуру
 * - Тестировать подключение к ComfyUI
 */
const WorkflowManager = () => {
  const [workflows, setWorkflows] = useState({
    text2img: null,
    img2img: null,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [comfyStatus, setComfyStatus] = useState(null);
  const [selectedWorkflow, setSelectedWorkflow] = useState(null);
  const [workflowContent, setWorkflowContent] = useState('');
  const [isEditing, setIsEditing] = useState(false);

  // Проверка статуса ComfyUI
  const checkComfyStatus = async () => {
    try {
      setLoading(true);
      // Используем proxy через nginx для доступа к ComfyUI
      // В production (Docker) используем относительный путь через nginx proxy
      // В development можно использовать переменную окружения или прямой URL
      const comfyUrl = import.meta.env.VITE_COMFY_URL || '/comfyui';
      const response = await fetch(`${comfyUrl}/system_stats`);
      if (response.ok) {
        const data = await response.json();
        setComfyStatus({ connected: true, data });
        setSuccess('ComfyUI подключен успешно');
        setError(null);
      } else {
        setComfyStatus({ connected: false });
        setError('ComfyUI недоступен');
      }
    } catch (err) {
      setComfyStatus({ connected: false });
      setError(`Ошибка подключения к ComfyUI: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // Загрузка workflow файла
  const handleFileUpload = async (type, file) => {
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
      try {
        const content = e.target.result;
        const json = JSON.parse(content);
        
        // Валидация структуры
        const validation = validateWorkflow(json, type);
        if (!validation.valid) {
          setError(`Ошибка валидации: ${validation.error}`);
          return;
        }

        setWorkflows(prev => ({
          ...prev,
          [type]: { content: json, filename: file.name, raw: content }
        }));
        setSuccess(`Workflow ${type} загружен успешно`);
        setError(null);
      } catch (err) {
        setError(`Ошибка парсинга JSON: ${err.message}`);
      }
    };
    reader.readAsText(file);
  };

  // Валидация workflow структуры
  const validateWorkflow = (workflow, type) => {
    if (!workflow || typeof workflow !== 'object') {
      return { valid: false, error: 'Workflow должен быть объектом' };
    }

    const nodes = Object.values(workflow);
    const nodeTypes = nodes.map(n => n.class_type);

    // Проверка обязательных нод
    const requiredNodes = {
      text2img: ['CheckpointLoaderSimple', 'KSampler', 'CLIPTextEncode', 'EmptyLatentImage', 'VAEDecode', 'SaveImage'],
      img2img: ['CheckpointLoaderSimple', 'KSampler', 'CLIPTextEncode', 'LoadImage', 'VAEEncode', 'VAEDecode', 'SaveImage'],
    };

    const required = requiredNodes[type] || [];
    const missing = required.filter(node => !nodeTypes.includes(node));

    if (missing.length > 0) {
      return { valid: false, error: `Отсутствуют обязательные ноды: ${missing.join(', ')}` };
    }

    // Проверка наличия KSampler
    const ksampler = nodes.find(n => n.class_type === 'KSampler');
    if (!ksampler) {
      return { valid: false, error: 'KSampler не найден' };
    }

    return { valid: true };
  };

  // Сохранение workflow на сервер
  const saveWorkflow = async (type) => {
    if (!workflows[type]) {
      setError(`Workflow ${type} не загружен`);
      return;
    }

    try {
      setLoading(true);
      const blob = new Blob([workflows[type].raw], { type: 'application/json' });
      const file = new File([blob], `${type}.json`, { type: 'application/json' });
      
      const result = await uploadWorkflow(type, file);

      if (result.success) {
        setSuccess(result.message || `Workflow ${type} сохранен на сервере`);
        setError(null);
      } else {
        setError(`Ошибка сохранения: ${result.error || 'Неизвестная ошибка'}`);
      }
    } catch (err) {
      setError(`Ошибка: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // Просмотр workflow
  const viewWorkflow = (type) => {
    if (!workflows[type]) {
      setError(`Workflow ${type} не загружен`);
      return;
    }
    setSelectedWorkflow(type);
    setWorkflowContent(JSON.stringify(workflows[type].content, null, 2));
    setIsEditing(false);
  };

  // Редактирование workflow
  const editWorkflow = (type) => {
    viewWorkflow(type);
    setIsEditing(true);
  };

  // Сохранение изменений
  const saveWorkflowChanges = () => {
    try {
      const json = JSON.parse(workflowContent);
      const validation = validateWorkflow(json, selectedWorkflow);
      if (!validation.valid) {
        setError(`Ошибка валидации: ${validation.error}`);
        return;
      }

      setWorkflows(prev => ({
        ...prev,
        [selectedWorkflow]: {
          ...prev[selectedWorkflow],
          content: json,
          raw: workflowContent
        }
      }));
      setIsEditing(false);
      setSuccess('Изменения сохранены');
    } catch (err) {
      setError(`Ошибка парсинга JSON: ${err.message}`);
    }
  };

  // Проверка при загрузке
  useEffect(() => {
    checkComfyStatus();
  }, []);

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white flex items-center gap-2">
            <Settings className="w-6 h-6" />
            Управление Workflow
          </h2>
          <p className="text-gray-400 mt-1">
            Загрузка и настройка ComfyUI workflow файлов
          </p>
        </div>
        <button
          onClick={checkComfyStatus}
          disabled={loading}
          className="px-4 py-2 bg-wink-orange text-black rounded-lg hover:bg-wink-orange/90 disabled:opacity-50 flex items-center gap-2"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Проверить ComfyUI
        </button>
      </div>

      {/* Статус ComfyUI */}
      <div className={`p-4 rounded-lg border ${
        comfyStatus?.connected 
          ? 'border-green-500 bg-green-500/10' 
          : 'border-red-500 bg-red-500/10'
      }`}>
        <div className="flex items-center gap-2">
          {comfyStatus?.connected ? (
            <CheckCircle2 className="w-5 h-5 text-green-500" />
          ) : (
            <AlertCircle className="w-5 h-5 text-red-500" />
          )}
          <span className="font-semibold">
            {comfyStatus?.connected ? 'ComfyUI подключен' : 'ComfyUI недоступен'}
          </span>
        </div>
        {comfyStatus?.connected && (
          <p className="text-sm text-gray-400 mt-1">
            URL: http://127.0.0.1:8188
          </p>
        )}
      </div>

      {/* Сообщения об ошибках/успехе */}
      {error && (
        <div className="p-4 bg-red-500/10 border border-red-500 rounded-lg flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-500" />
          <span className="text-red-500">{error}</span>
          <button onClick={() => setError(null)} className="ml-auto">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {success && (
        <div className="p-4 bg-green-500/10 border border-green-500 rounded-lg flex items-center gap-2">
          <CheckCircle2 className="w-5 h-5 text-green-500" />
          <span className="text-green-500">{success}</span>
          <button onClick={() => setSuccess(null)} className="ml-auto">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Список workflow */}
      <div className="grid md:grid-cols-2 gap-4">
        {/* Text2Img Workflow */}
        <div className="border border-wink-gray rounded-lg p-4 bg-wink-dark">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                <FileJson className="w-5 h-5" />
                Text2Img Workflow
              </h3>
              <p className="text-sm text-gray-400 mt-1">
                Генерация изображений с нуля
              </p>
            </div>
            {workflows.text2img && (
              <CheckCircle2 className="w-5 h-5 text-green-500" />
            )}
          </div>

          <div className="space-y-2">
            <label className="block">
              <span className="text-sm text-gray-400 mb-2 block">Загрузить файл</span>
              <input
                type="file"
                accept=".json"
                onChange={(e) => handleFileUpload('text2img', e.target.files[0])}
                className="block w-full text-sm text-gray-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-wink-orange file:text-black hover:file:bg-wink-orange/90"
              />
            </label>

            {workflows.text2img && (
              <div className="flex gap-2">
                <button
                  onClick={() => viewWorkflow('text2img')}
                  className="flex-1 px-3 py-2 bg-wink-gray hover:bg-wink-gray/80 rounded-lg text-sm flex items-center justify-center gap-2"
                >
                  <Eye className="w-4 h-4" />
                  Просмотр
                </button>
                <button
                  onClick={() => editWorkflow('text2img')}
                  className="flex-1 px-3 py-2 bg-wink-gray hover:bg-wink-gray/80 rounded-lg text-sm flex items-center justify-center gap-2"
                >
                  <Settings className="w-4 h-4" />
                  Редактировать
                </button>
                <button
                  onClick={() => saveWorkflow('text2img')}
                  disabled={loading}
                  className="px-3 py-2 bg-wink-orange text-black rounded-lg hover:bg-wink-orange/90 disabled:opacity-50 flex items-center gap-2"
                >
                  {loading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Img2Img Workflow */}
        <div className="border border-wink-gray rounded-lg p-4 bg-wink-dark">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                <FileJson className="w-5 h-5" />
                Img2Img Workflow
              </h3>
              <p className="text-sm text-gray-400 mt-1">
                Генерация из существующего изображения
              </p>
            </div>
            {workflows.img2img && (
              <CheckCircle2 className="w-5 h-5 text-green-500" />
            )}
          </div>

          <div className="space-y-2">
            <label className="block">
              <span className="text-sm text-gray-400 mb-2 block">Загрузить файл</span>
              <input
                type="file"
                accept=".json"
                onChange={(e) => handleFileUpload('img2img', e.target.files[0])}
                className="block w-full text-sm text-gray-400 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-wink-orange file:text-black hover:file:bg-wink-orange/90"
              />
            </label>

            {workflows.img2img && (
              <div className="flex gap-2">
                <button
                  onClick={() => viewWorkflow('img2img')}
                  className="flex-1 px-3 py-2 bg-wink-gray hover:bg-wink-gray/80 rounded-lg text-sm flex items-center justify-center gap-2"
                >
                  <Eye className="w-4 h-4" />
                  Просмотр
                </button>
                <button
                  onClick={() => editWorkflow('img2img')}
                  className="flex-1 px-3 py-2 bg-wink-gray hover:bg-wink-gray/80 rounded-lg text-sm flex items-center justify-center gap-2"
                >
                  <Settings className="w-4 h-4" />
                  Редактировать
                </button>
                <button
                  onClick={() => saveWorkflow('img2img')}
                  disabled={loading}
                  className="px-3 py-2 bg-wink-orange text-black rounded-lg hover:bg-wink-orange/90 disabled:opacity-50 flex items-center gap-2"
                >
                  {loading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Модальное окно просмотра/редактирования */}
      {selectedWorkflow && (
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
          <div className="bg-wink-dark border border-wink-gray rounded-lg max-w-4xl w-full max-h-[90vh] flex flex-col">
            <div className="p-4 border-b border-wink-gray flex items-center justify-between">
              <h3 className="text-lg font-semibold text-white">
                {isEditing ? 'Редактирование' : 'Просмотр'} {selectedWorkflow}.json
              </h3>
              <div className="flex gap-2">
                {isEditing && (
                  <button
                    onClick={saveWorkflowChanges}
                    className="px-4 py-2 bg-wink-orange text-black rounded-lg hover:bg-wink-orange/90 flex items-center gap-2"
                  >
                    <Save className="w-4 h-4" />
                    Сохранить
                  </button>
                )}
                <button
                  onClick={() => {
                    setSelectedWorkflow(null);
                    setIsEditing(false);
                  }}
                  className="px-4 py-2 bg-wink-gray hover:bg-wink-gray/80 rounded-lg"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>
            <div className="flex-1 overflow-auto p-4">
              {isEditing ? (
                <textarea
                  value={workflowContent}
                  onChange={(e) => setWorkflowContent(e.target.value)}
                  className="w-full h-full font-mono text-sm bg-wink-black text-white p-4 rounded-lg border border-wink-gray resize-none"
                  style={{ minHeight: '400px' }}
                />
              ) : (
                <pre className="text-sm text-gray-300 overflow-auto bg-wink-black p-4 rounded-lg border border-wink-gray">
                  {workflowContent}
                </pre>
              )}
            </div>
            <div className="p-4 border-t border-wink-gray bg-wink-black/50">
              <div className="flex items-start gap-2 text-sm text-gray-400">
                <Info className="w-4 h-4 mt-0.5 flex-shrink-0" />
                <div>
                  <p className="font-semibold mb-1">Информация:</p>
                  <ul className="list-disc list-inside space-y-1">
                    <li>Workflow должен быть валидным JSON</li>
                    <li>Обязательные ноды: CheckpointLoaderSimple, KSampler, CLIPTextEncode, SaveImage</li>
                    <li>Для text2img требуется EmptyLatentImage</li>
                    <li>Для img2img требуется LoadImage и VAEEncode</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Инструкция */}
      <div className="border border-wink-gray rounded-lg p-4 bg-wink-dark">
        <h3 className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
          <Info className="w-5 h-5" />
          Инструкция
        </h3>
        <ol className="list-decimal list-inside space-y-2 text-gray-400 text-sm">
          <li>Проверьте подключение к ComfyUI (кнопка "Проверить ComfyUI")</li>
          <li>Загрузите workflow файлы через форму выше или создайте их в ComfyUI GUI</li>
          <li>Проверьте валидность загруженных файлов</li>
          <li>Сохраните workflow на сервер для использования в генерации</li>
          <li>При необходимости отредактируйте workflow через интерфейс</li>
        </ol>
      </div>
    </div>
  );
};

export default WorkflowManager;

