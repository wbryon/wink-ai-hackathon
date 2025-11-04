import { useState } from 'react';
import UploadScene from '../components/UploadScene';
import SceneList from '../components/SceneList';
import FrameViewer from '../components/FrameViewer';
import { mockScenes, mockScriptData } from '../utils/mockData';

const STEPS = {
  UPLOAD: 'upload',
  REVIEW: 'review',
  GENERATE: 'generate',
};

const MainPage = () => {
  const [currentStep, setCurrentStep] = useState(STEPS.UPLOAD);
  const [scriptData, setScriptData] = useState(null);
  const [scenes, setScenes] = useState([]);
  const [showDevMenu, setShowDevMenu] = useState(true); // Dev menu для демо

  // Обработка успешной загрузки сценария
  const handleUploadSuccess = (data) => {
    setScriptData(data);
    setScenes(data.scenes || []);
    setCurrentStep(STEPS.REVIEW);
  };

  // Переход к генерации
  const handleContinueToGeneration = (updatedScenes) => {
    setScenes(updatedScenes);
    setCurrentStep(STEPS.GENERATE);
  };

  // Рендер компонента в зависимости от текущего шага
  const renderStep = () => {
    switch (currentStep) {
      case STEPS.UPLOAD:
        return <UploadScene onUploadSuccess={handleUploadSuccess} />;
      
      case STEPS.REVIEW:
        return (
          <SceneList
            scenes={scenes}
            scriptId={scriptData?.scriptId}
            onContinue={handleContinueToGeneration}
          />
        );
      
      case STEPS.GENERATE:
        return (
          <FrameViewer
            scenes={scenes}
            scriptId={scriptData?.scriptId}
          />
        );
      
      default:
        return <UploadScene onUploadSuccess={handleUploadSuccess} />;
    }
  };

  // Dev функции для быстрой навигации
  const loadMockData = () => {
    setScriptData(mockScriptData);
    setScenes(mockScenes);
  };

  const goToUpload = () => setCurrentStep(STEPS.UPLOAD);
  
  const goToReview = () => {
    if (!scenes.length) loadMockData();
    setCurrentStep(STEPS.REVIEW);
  };

  const goToGenerate = () => {
    if (!scenes.length) loadMockData();
    setCurrentStep(STEPS.GENERATE);
  };

  return (
    <div className="min-h-screen bg-wink-black text-white">
      {/* Dev Menu для быстрой навигации */}
      {showDevMenu && (
        <div className="fixed top-4 right-4 z-50 bg-wink-dark border-2 border-wink-orange rounded-lg p-4 shadow-2xl">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-bold text-wink-orange">🔧 DEV МЕНЮ</span>
            <button
              onClick={() => setShowDevMenu(false)}
              className="text-gray-400 hover:text-white"
            >
              ✕
            </button>
          </div>
          <div className="space-y-2">
            <button
              onClick={goToUpload}
              className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                currentStep === STEPS.UPLOAD 
                  ? 'bg-wink-orange text-black font-bold' 
                  : 'bg-wink-gray hover:bg-wink-orange hover:text-black'
              }`}
            >
              📄 Экран 1: Загрузка
            </button>
            <button
              onClick={goToReview}
              className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                currentStep === STEPS.REVIEW 
                  ? 'bg-wink-orange text-black font-bold' 
                  : 'bg-wink-gray hover:bg-wink-orange hover:text-black'
              }`}
            >
              🎬 Экран 2: Редактирование
            </button>
            <button
              onClick={goToGenerate}
              className={`w-full text-left px-3 py-2 rounded text-sm transition-colors ${
                currentStep === STEPS.GENERATE 
                  ? 'bg-wink-orange text-black font-bold' 
                  : 'bg-wink-gray hover:bg-wink-orange hover:text-black'
              }`}
            >
              🎨 Экран 3: Генерация
            </button>
          </div>
          <div className="mt-3 pt-3 border-t border-wink-gray">
            <button
              onClick={loadMockData}
              className="w-full px-3 py-2 bg-green-600 hover:bg-green-700 rounded text-sm font-bold transition-colors"
            >
              💾 Загрузить тестовые данные
            </button>
          </div>
        </div>
      )}

      {/* Кнопка для показа меню если оно скрыто */}
      {!showDevMenu && (
        <button
          onClick={() => setShowDevMenu(true)}
          className="fixed top-4 right-4 z-50 bg-wink-orange text-black px-4 py-2 rounded-lg font-bold shadow-lg hover:scale-105 transition-transform"
        >
          🔧 DEV
        </button>
      )}

      {renderStep()}
    </div>
  );
};

export default MainPage;

