import axios from 'axios';
import { mockAPI } from '../utils/mockData';

// Базовый URL для API (замените на ваш backend URL)
const API_BASE_URL = 'http://localhost:8080/api';
const USE_MOCKS = (import.meta?.env?.VITE_USE_MOCKS === 'true') || (typeof window !== 'undefined' && window.USE_MOCKS === true);

// Создаем экземпляр axios с базовыми настройками
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 120000, // 2 минуты для генерации изображений
});

// Интерцептор для обработки ошибок
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error);
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
      const response = await apiClient.post('/scripts/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
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
 * @param {string} detailLevel - Уровень детализации ('sketch', 'medium', 'final')
 * @returns {Promise} - Промис с URL сгенерированного изображения
 */
export const generateFrame = async (sceneId, detailLevel = 'medium') => {
  return maybeFallback(
    async () => (await apiClient.post(`/scenes/${sceneId}/generate`, { detailLevel })).data,
    async () => mockAPI.generateFrame(sceneId, detailLevel)
  );
};

/**
 * Регенерация изображения с новым промптом
 * @param {string} frameId - ID кадра
 * @param {string} prompt - Новый промпт для генерации
 * @param {string} detailLevel - Уровень детализации
 * @returns {Promise} - Промис с URL нового изображения
 */
export const regenerateFrame = async (frameId, prompt, detailLevel) => {
  return maybeFallback(
    async () => (await apiClient.post(`/frames/${frameId}/regenerate`, { prompt, detailLevel })).data,
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

export default apiClient;

