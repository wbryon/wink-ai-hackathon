import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import UploadScene from '../components/UploadScene';
import SceneList from '../components/SceneList';
import FrameViewer from '../components/FrameViewer';
import { mockScenes, mockScriptData } from '../utils/mockData';
import { Settings, Loader2 } from 'lucide-react';
import { getScenes, getScriptStatus } from '../api/apiClient';

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
  const [parsingStatus, setParsingStatus] = useState(null);
  const [isPolling, setIsPolling] = useState(false);
  const pollingTimeoutRef = useRef(null);
  const isPollingRef = useRef(false);

  // Обработка успешной загрузки сценария
  const handleUploadSuccess = async (data) => {
    setScriptData(data);
    setScenes(data.scenes || []);
    setCurrentStep(STEPS.REVIEW);
    
    // Если сцены еще не готовы, начинаем опрос статуса
    if (data.scriptId && (!data.scenes || data.scenes.length === 0)) {
      setIsPolling(true);
      isPollingRef.current = true;
      startPollingForScenes(data.scriptId);
    }
  };

  // Опрос статуса парсинга и загрузка сцен, когда они будут готовы
  const startPollingForScenes = async (scriptId) => {
    // Очищаем предыдущий таймер, если есть
    if (pollingTimeoutRef.current) {
      clearTimeout(pollingTimeoutRef.current);
      pollingTimeoutRef.current = null;
    }
    
    const maxAttempts = 60; // максимум 60 попыток (3 минуты при интервале 3 сек)
    let attempts = 0;
    
    const poll = async () => {
      // Проверяем, не остановлен ли опрос
      if (!isPollingRef.current || attempts >= maxAttempts) {
        setIsPolling(false);
        isPollingRef.current = false;
        if (pollingTimeoutRef.current) {
          clearTimeout(pollingTimeoutRef.current);
          pollingTimeoutRef.current = null;
        }
        return;
      }
      
      try {
        const status = await getScriptStatus(scriptId);
        setParsingStatus(status);
        
        // Если статус PARSED и есть сцены, загружаем их
        if (status.scriptStatus === 'PARSED' && status.totalScenes > 0) {
          const loadedScenes = await getScenes(scriptId);
          if (loadedScenes && loadedScenes.length > 0) {
            setScenes(loadedScenes);
            setIsPolling(false);
            isPollingRef.current = false;
            if (pollingTimeoutRef.current) {
              clearTimeout(pollingTimeoutRef.current);
              pollingTimeoutRef.current = null;
            }
            return;
          }
        }
        
        // Если статус FAILED, прекращаем опрос
        if (status.scriptStatus === 'FAILED') {
          setIsPolling(false);
          isPollingRef.current = false;
          if (pollingTimeoutRef.current) {
            clearTimeout(pollingTimeoutRef.current);
            pollingTimeoutRef.current = null;
          }
          return;
        }
        
        attempts++;
        // Опрашиваем каждые 3 секунды
        if (isPollingRef.current && attempts < maxAttempts) {
          pollingTimeoutRef.current = setTimeout(poll, 3000);
        } else {
          setIsPolling(false);
          isPollingRef.current = false;
        }
      } catch (error) {
        console.error('Error polling script status:', error);
        attempts++;
        if (isPollingRef.current && attempts < maxAttempts) {
          pollingTimeoutRef.current = setTimeout(poll, 3000);
        } else {
          setIsPolling(false);
          isPollingRef.current = false;
          if (pollingTimeoutRef.current) {
            clearTimeout(pollingTimeoutRef.current);
            pollingTimeoutRef.current = null;
          }
        }
      }
    };
    
    poll();
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
          <>
            {/* Индикатор обработки файла */}
            {isPolling && parsingStatus && (
              <div className="max-w-6xl mx-auto px-4 py-6">
                <div className="bg-wink-black/50 border border-wink-gray/30 rounded-lg p-6">
                  <div className="flex items-center gap-4 mb-4">
                    <Loader2 className="w-6 h-6 text-wink-orange animate-spin" />
                    <div>
                      <h3 className="text-lg font-bold text-white">Обработка файла...</h3>
                      <p className="text-sm text-gray-400">
                        Статус: {parsingStatus.scriptStatus === 'PARSING' ? 'Парсинг сценария' : 
                                 parsingStatus.scriptStatus === 'UPLOADED' ? 'Файл загружен' : 
                                 parsingStatus.scriptStatus}
                      </p>
                    </div>
                  </div>
                  
                  {parsingStatus.totalScenes > 0 && (
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-400">Прогресс парсинга:</span>
                        <span className="text-wink-orange font-bold">
                          {parsingStatus.parsedScenes} / {parsingStatus.totalScenes} сцен
                        </span>
                      </div>
                      <div className="w-full bg-wink-dark rounded-full h-2">
                        <div 
                          className="bg-wink-orange h-2 rounded-full transition-all duration-300"
                          style={{ width: `${parsingStatus.completionPercent || 0}%` }}
                        />
                      </div>
                      <div className="flex gap-4 text-xs text-gray-500">
                        <span>Ожидают: {parsingStatus.pendingScenes || 0}</span>
                        <span>Обрабатываются: {parsingStatus.processingScenes || 0}</span>
                        <span>Готовы: {parsingStatus.parsedScenes || 0}</span>
                        {parsingStatus.failedScenes > 0 && (
                          <span className="text-red-400">Ошибки: {parsingStatus.failedScenes}</span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}
            
            <SceneList
              scenes={scenes}
              scriptId={scriptData?.scriptId}
              onContinue={handleContinueToGeneration}
            />
          </>
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
  const canGoToReview = !!scriptData; // Можно перейти к просмотру сцен, даже если они еще обрабатываются
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

  // При переходе на REVIEW, если сцены еще не загружены, начинаем опрос
  useEffect(() => {
    if (currentStep === STEPS.REVIEW && scriptData?.scriptId && scenes.length === 0 && !isPollingRef.current) {
      setIsPolling(true);
      isPollingRef.current = true;
      startPollingForScenes(scriptData.scriptId);
    }
    
    // Очистка при размонтировании или изменении зависимостей
    return () => {
      setIsPolling(false);
      isPollingRef.current = false;
      if (pollingTimeoutRef.current) {
        clearTimeout(pollingTimeoutRef.current);
        pollingTimeoutRef.current = null;
      }
    };
  }, [currentStep, scriptData?.scriptId, scenes.length]);

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

