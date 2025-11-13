import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import UploadScene from '../components/UploadScene';
import SceneList from '../components/SceneList';
import FrameViewer from '../components/FrameViewer';
import { mockScenes, mockScriptData } from '../utils/mockData';
import { Settings } from 'lucide-react';

const STEPS = {
  UPLOAD: 'upload',
  REVIEW: 'review',
  GENERATE: 'generate',
};

const MainPage = () => {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(STEPS.UPLOAD);
  const [scriptData, setScriptData] = useState(null);
  const [scenes, setScenes] = useState([]);

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

  // Навигация по шагам
  const canGoToReview = !!scriptData && scenes.length > 0;
  const canGoToGenerate = !!scriptData && scenes.length > 0;

  const goToUpload = () => setCurrentStep(STEPS.UPLOAD);
  const goToReview = () => {
    if (!canGoToReview) return;
    setCurrentStep(STEPS.REVIEW);
  };
  const goToGenerate = () => {
    if (!canGoToGenerate) return;
    setCurrentStep(STEPS.GENERATE);
  };

  // При монтировании страницы — отключаем автоматическое восстановление скролла
  // и принудительно прокручиваем страницу наверх, чтобы избежать накопления смещения
  useEffect(() => {
    try {
      if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
    } catch (e) {
      // ignore (SSR or restricted env)
    }
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="min-h-screen bg-wink-black text-white">
      {/* Верхняя навигация по шагам */}
      <header className="border-b border-wink-gray bg-wink-dark/80 sticky top-0 z-40 backdrop-blur">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img
              src="/images/wink-logo.webp"
              alt="Wink"
              className="h-8 filter brightness-0 invert"
            />
            <div>
              <div className="text-sm uppercase tracking-widest text-gray-400">
                Wink PreViz
              </div>
              <div className="text-lg font-cofo-black text-gradient-wink">
                Storyboard Studio
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <nav className="flex items-center gap-2 text-xs md:text-sm">
            <button
              onClick={goToUpload}
              className={`px-3 py-2 rounded-lg border transition-all ${
                currentStep === STEPS.UPLOAD
                  ? 'border-wink-orange bg-wink-orange text-black font-bold'
                  : 'border-transparent bg-wink-black hover:border-wink-orange/60'
              }`}
            >
              1. Загрузка
            </button>
            <button
              onClick={goToReview}
              disabled={!canGoToReview}
              className={`px-3 py-2 rounded-lg border transition-all ${
                currentStep === STEPS.REVIEW
                  ? 'border-wink-orange bg-wink-orange text-black font-bold'
                  : 'border-transparent bg-wink-black hover:border-wink-orange/60'
              } ${!canGoToReview ? 'opacity-40 cursor-not-allowed' : ''}`}
            >
              2. Сцены
            </button>
            <button
              onClick={goToGenerate}
              disabled={!canGoToGenerate}
              className={`px-3 py-2 rounded-lg border transition-all ${
                currentStep === STEPS.GENERATE
                  ? 'border-wink-orange bg-wink-orange text-black font-bold'
                  : 'border-transparent bg-wink-black hover:border-wink-orange/60'
              } ${!canGoToGenerate ? 'opacity-40 cursor-not-allowed' : ''}`}
            >
              3. Кадры
            </button>
          </nav>
          
          <button
            onClick={() => navigate('/settings')}
            className="p-2 hover:bg-wink-gray rounded-lg transition-colors"
            title="Настройки"
          >
            <Settings className="w-5 h-5" />
          </button>
          </div>
        </div>
      </header>

      {renderStep()}
    </div>
  );
};

export default MainPage;

