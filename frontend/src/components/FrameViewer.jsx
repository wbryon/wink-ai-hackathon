import { useState, useEffect } from 'react';
import { 
  Loader2, 
  Download, 
  RefreshCw, 
  Edit3, 
  Save,
  X,
  Image as ImageIcon,
  Sparkles,
  FileImage,
  ChevronLeft,
  ChevronRight,
  History,
  Upload as UploadIcon,
  FileText,
  AlertTriangle,
  Users,
  MapPin,
  Zap,
  Camera,
  Palette,
  Brush,
  Ban,
} from 'lucide-react';
import { 
  generateFrame, 
  generateProgressiveFrame,
  regenerateFrame, 
  getFrameHistory, 
  exportStoryboard,
  updateScene,
  getFrameCards,
  getSceneVisual,
  getSlots,
  updateSlots,
} from '../api/apiClient';
import PromptSlotsEditor from './PromptSlotsEditor';

const DETAIL_LEVELS = [
  { id: 'sketch', name: 'Эскиз', icon: '🖊️', description: 'Черно-белый набросок' },
  { id: 'mid', name: 'Средняя детализация', icon: '🎨', description: 'Цветное изображение' },
  { id: 'final', name: 'Финальный кадр', icon: '✨', description: 'Детализированный стиль' },
];

const FrameViewer = ({ scenes: initialScenes, scriptId }) => {
  const [scenes, setScenes] = useState(initialScenes || []);
  const [currentSceneIndex, setCurrentSceneIndex] = useState(0);
  const [detailLevel, setDetailLevel] = useState('sketch');
  const [directFinal, setDirectFinal] = useState(false);
  const [generationPath, setGenerationPath] = useState(null); // 'progressive' | 'direct' | null
  const [isGenerating, setIsGenerating] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [isExportingMeta, setIsExportingMeta] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState(false);
  const [promptText, setPromptText] = useState('');
  const [frameHistory, setFrameHistory] = useState([]);
  const [showHistory, setShowHistory] = useState(false);
  const [visualSlots, setVisualSlots] = useState(null);
  const [showGallery, setShowGallery] = useState(false);
  const [galleryLoading, setGalleryLoading] = useState(false);
  const [galleryCards, setGalleryCards] = useState([]);
  const [galleryLodFilter, setGalleryLodFilter] = useState('all');
  const [editingSlots, setEditingSlots] = useState(false);
  const [previewPrompt, setPreviewPrompt] = useState('');

  const currentScene = scenes[currentSceneIndex];

  // Проверяем наличие Sketch кадра для текущей сцены
  const hasSketchFrame = () => {
    if (!currentScene?.id) return false;
    return frameHistory.some(frame => 
      frame.detailLevel === 'sketch' || frame.lod === 'sketch'
    );
  };

  // Загрузить историю генераций при смене сцены
  useEffect(() => {
    if (currentScene?.id) {
      loadFrameHistory();
      // Определяем path из текущего кадра, если есть
      if (currentScene?.currentFrame?.path) {
        setGenerationPath(currentScene.currentFrame.path);
      } else if (currentScene?.currentFrame?.detailLevel === 'direct_final') {
        setGenerationPath('direct');
        setDirectFinal(true);
      } else {
        setGenerationPath(null);
        setDirectFinal(false);
      }
    }
  }, [currentSceneIndex]);

  // Загрузить enriched JSON / слоты при смене сцены
  useEffect(() => {
    const loadVisual = async () => {
      if (!currentScene?.id) {
        setVisualSlots(null);
        return;
      }
      try {
        const visual = await getSceneVisual(currentScene.id);
        setVisualSlots(visual?.slots || null);
      } catch (error) {
        console.error('Error loading scene visual:', error);
        setVisualSlots(null);
      }
    };

    loadVisual();
  }, [currentScene?.id]);

  // Обработка сохранения слотов
  const handleSaveSlots = async (updatedSlots) => {
    if (!currentScene?.id) return;

    try {
      const result = await updateSlots(currentScene.id, updatedSlots, false);
      // Обновляем локальное состояние
      setVisualSlots(result?.slots || updatedSlots);
      setEditingSlots(false);
      
      // Показываем уведомление об успехе
      alert('Слоты успешно сохранены. Промпт будет пересобран.');
    } catch (error) {
      console.error('Error saving slots:', error);
      throw error; // Пробрасываем ошибку для обработки в компоненте
    }
  };

  // Открыть редактор слотов
  const handleEditSlots = async () => {
    if (!currentScene?.id) return;

    try {
      // Загружаем текущие слоты
      const slots = await getSlots(currentScene.id);
      setVisualSlots(slots);
      setEditingSlots(true);
    } catch (error) {
      console.error('Error loading slots:', error);
      // Если слоты еще не созданы, начинаем с пустых
      setEditingSlots(true);
    }
  };

  // Загрузить историю генераций
  const loadFrameHistory = async () => {
    try {
      const history = await getFrameHistory(currentScene.id);
      setFrameHistory(history || []);
    } catch (error) {
      console.error('Error loading frame history:', error);
    }
  };

  // Генерация кадра
  const handleGenerate = async () => {
    if (!currentScene) return;

    setIsGenerating(true);
    try {
      let response;
      
      if (directFinal) {
        // Direct Final: сразу финал без эскиза
        response = await generateFrame(
          currentScene.id, 
          'direct_final', 
          'direct'
        );
        setGenerationPath('direct');
      } else if (detailLevel === 'mid' || detailLevel === 'final') {
        // Progressive path: Sketch → Mid → Final
        // Используем специальный endpoint для progressive генерации
        response = await generateProgressiveFrame(currentScene.id, detailLevel);
        setGenerationPath('progressive');
      } else {
        // Sketch: обычная генерация
        response = await generateFrame(
          currentScene.id, 
          detailLevel, 
          'progressive'
        );
        setGenerationPath('progressive');
      }
      
      // Обновляем сцену с новым кадром
      const updatedScenes = [...scenes];
      updatedScenes[currentSceneIndex] = {
        ...currentScene,
        currentFrame: response,
        generatedFrames: [...(currentScene.generatedFrames || []), response],
      };
      setScenes(updatedScenes);
      
      // Обновляем историю
      await loadFrameHistory();
    } catch (error) {
      console.error('Error generating frame:', error);
      alert('Ошибка при генерации изображения. Попробуйте еще раз.');
    } finally {
      setIsGenerating(false);
    }
  };

  // Регенерация с новым промптом
  const handleRegenerate = async () => {
    if (!currentScene?.currentFrame?.id || !promptText.trim()) return;

    setIsGenerating(true);
    try {
      const currentPath = currentScene.currentFrame.path || generationPath;
      const response = await regenerateFrame(
        currentScene.currentFrame.id,
        promptText,
        directFinal ? 'direct_final' : detailLevel,
        currentPath
      );
      
      const updatedScenes = [...scenes];
      updatedScenes[currentSceneIndex] = {
        ...currentScene,
        currentFrame: response,
        prompt: promptText,
      };
      setScenes(updatedScenes);
      setEditingPrompt(false);
      
      await loadFrameHistory();
    } catch (error) {
      console.error('Error regenerating frame:', error);
      alert('Ошибка при регенерации изображения. Попробуйте еще раз.');
    } finally {
      setIsGenerating(false);
    }
  };

  // Сохранение отредактированного промпта
  const handleSavePrompt = async () => {
    try {
      await updateScene(currentScene.id, {
        ...currentScene,
        prompt: promptText,
      });
      
      const updatedScenes = [...scenes];
      updatedScenes[currentSceneIndex] = {
        ...currentScene,
        prompt: promptText,
      };
      setScenes(updatedScenes);
      setEditingPrompt(false);
    } catch (error) {
      console.error('Error saving prompt:', error);
    }
  };

  // Экспорт storyboard
  const handleExport = async () => {
    setIsExporting(true);
    try {
      const blob = await exportStoryboard(scriptId);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `storyboard-${Date.now()}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error exporting storyboard:', error);
      alert('Ошибка при экспорте. Попробуйте еще раз.');
    } finally {
      setIsExporting(false);
    }
  };

  // Экспорт frame-card (метаданных) в JSON
  const handleExportMeta = async () => {
    if (!scriptId) return;
    setIsExportingMeta(true);
    try {
      const cards = await getFrameCards(scriptId);
      const blob = new Blob([JSON.stringify(cards || [], null, 2)], {
        type: 'application/json',
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `frame-cards-${scriptId}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error exporting frame cards:', error);
      alert('Ошибка при экспорте метаданных. Попробуйте еще раз.');
    } finally {
      setIsExportingMeta(false);
    }
  };

  // Открыть/закрыть галерею кадров
  const handleToggleGallery = async () => {
    if (!scriptId) return;

    // если уже загружали — просто переключаем видимость
    if (galleryCards.length > 0) {
      setShowGallery(!showGallery);
      return;
    }

    setGalleryLoading(true);
    try {
      const cards = await getFrameCards(scriptId);
      setGalleryCards(cards || []);
      setShowGallery(true);
    } catch (error) {
      console.error('Error loading frame cards gallery:', error);
      alert('Ошибка при загрузке галереи кадров. Попробуйте ещё раз.');
    } finally {
      setGalleryLoading(false);
    }
  };

  const filteredGalleryCards = galleryCards.filter((card) =>
    galleryLodFilter === 'all' ? true : card.lod === galleryLodFilter
  );

  // Начать редактирование промпта
  const startEditingPrompt = () => {
    setPromptText(currentScene?.prompt || generateDefaultPrompt());
    setEditingPrompt(true);
  };

  // Генерация промпта по умолчанию
  const generateDefaultPrompt = () => {
    if (!currentScene) return '';
    
    let prompt = `${currentScene.description || ''}`;
    if (currentScene.location) prompt += ` Локация: ${currentScene.location}.`;
    if (currentScene.characters?.length) prompt += ` Персонажи: ${currentScene.characters.join(', ')}.`;
    if (currentScene.props?.length) prompt += ` Реквизит: ${currentScene.props.join(', ')}.`;
    
    return prompt.trim();
  };

  // Выбор кадра из истории
  const selectHistoryFrame = (frame) => {
    const updatedScenes = [...scenes];
    updatedScenes[currentSceneIndex] = {
      ...currentScene,
      currentFrame: frame,
    };
    setScenes(updatedScenes);
    setShowHistory(false);
  };

  return (
    <div className="min-h-screen flex flex-col animate-fadeInUp">
      {/* Шапка */}
      <header className="bg-wink-dark border-b border-wink-gray p-4 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <img 
              src="/images/wink-logo.webp" 
              alt="Wink" 
              className="h-8 filter brightness-0 invert"
            />
            <h1 className="text-xl font-cofo-black text-gradient-wink">
              Wink PreViz Studio
            </h1>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2"
            >
              <UploadIcon className="w-4 h-4" /> Новый сценарий
            </button>
            
            <button
              onClick={handleExport}
              disabled={isExporting}
              className="btn-wink flex items-center gap-2"
            >
              {isExporting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Экспорт...
                </>
              ) : (
                <>
                  <Download className="w-4 h-4" />
                  Экспорт PDF
                </>
              )}
            </button>

            <button
              onClick={handleToggleGallery}
              disabled={galleryLoading}
              className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2 text-sm"
            >
              {galleryLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Галерея...
                </>
              ) : (
                <>
                  <ImageIcon className="w-4 h-4" />
                  Галерея кадров
                </>
              )}
            </button>

            <button
              onClick={handleExportMeta}
              disabled={isExportingMeta}
              className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2 text-sm"
            >
              {isExportingMeta ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Метаданные...
                </>
              ) : (
                <>
                  <FileText className="w-4 h-4" />
                  Экспорт метаданных
                </>
              )}
            </button>
          </div>
        </div>
      </header>

      <div className="flex-1 flex overflow-hidden">
        {/* Левая панель - список сцен */}
        <aside className="w-80 bg-wink-dark border-r border-wink-gray overflow-y-auto">
          <div className="p-4">
            <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
              <FileImage className="w-5 h-5 text-wink-orange" />
              Сцены ({scenes.length})
            </h2>

            <div className="space-y-2">
              {scenes.map((scene, index) => (
                <button
                  key={scene.id || index}
                  onClick={() => setCurrentSceneIndex(index)}
                  className={`
                    w-full text-left p-3 rounded-lg transition-all
                    ${currentSceneIndex === index 
                      ? 'bg-wink-gradient text-wink-black' 
                      : 'bg-wink-black hover:bg-wink-gray'
                    }
                  `}
                >
                  <div className="flex items-start gap-3">
                    <div className={`
                      w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 font-bold
                      ${currentSceneIndex === index ? 'bg-wink-black text-wink-orange' : 'bg-wink-gray'}
                    `}>
                      {index + 1}
                    </div>
                    
                    <div className="flex-1 min-w-0">
                      <div className="font-bold truncate">
                        {scene.title || `Сцена ${index + 1}`}
                      </div>
                      <div className={`text-sm truncate ${currentSceneIndex === index ? 'text-wink-black/70' : 'text-gray-400'}`}>
                        {scene.location || 'Без локации'}
                      </div>
                      
                      {/* Статус генерации */}
                      <div className="mt-1">
                        {scene.currentFrame ? (
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className={`text-xs flex items-center gap-1 ${currentSceneIndex === index ? 'text-wink-black' : 'text-green-500'}`}>
                              <Sparkles className="w-3 h-3" /> Сгенерировано
                            </span>
                            {scene.currentFrame.detailLevel && (
                              <span className={`text-[10px] px-2 py-0.5 rounded-full border ${currentSceneIndex === index ? 'border-wink-black text-wink-black' : 'border-wink-gray text-gray-400'}`}>
                                LOD: {scene.currentFrame.detailLevel === 'medium' ? 'mid' : scene.currentFrame.detailLevel === 'direct_final' ? 'direct final' : scene.currentFrame.detailLevel}
                              </span>
                            )}
                            {(scene.currentFrame.path || scene.currentFrame.generationPath) && (
                              <span className={`text-[10px] px-2 py-0.5 rounded-full border ${currentSceneIndex === index ? 'border-wink-black text-wink-black' : 'border-wink-gray text-gray-400'}`}>
                                {(scene.currentFrame.path || scene.currentFrame.generationPath) === 'progressive' ? '→' : '→→'}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className={`text-xs ${currentSceneIndex === index ? 'text-wink-black/50' : 'text-gray-500'}`}>
                            Не сгенерировано
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Превью */}
                    {scene.currentFrame?.imageUrl && (
                      <img
                        src={scene.currentFrame.imageUrl}
                        alt="Превью"
                        className="w-16 h-16 object-cover rounded"
                      />
                    )}
                  </div>
                </button>
              ))}
            </div>
          </div>
        </aside>

        {/* Центральная область - просмотр кадра */}
        <main className="flex-1 flex flex-col overflow-hidden">
          <div className="flex-1 p-6 overflow-y-auto">
            <div className="max-w-5xl mx-auto">
              {/* Информация о сцене */}
              <div className="mb-6">
                <h2 className="text-2xl font-cofo-black mb-2">
                  {currentScene?.title || `Сцена ${currentSceneIndex + 1}`}
                </h2>
                {currentScene?.location && (
                  <p className="text-gray-400">{currentScene.location}</p>
                )}
                {currentScene?.description && (
                  <div className="mt-4 p-4 bg-wink-black rounded-lg">
                    <p className="text-gray-300 whitespace-pre-wrap leading-relaxed">
                      {currentScene.description}
                    </p>
                  </div>
                )}
              </div>

              {/* Область просмотра кадра */}
              <div className="bg-wink-dark rounded-lg p-6 mb-6">
                {currentScene?.currentFrame?.imageUrl ? (
                  <div className="relative">
                    <img
                      src={currentScene.currentFrame.imageUrl}
                      alt={currentScene.title}
                      className="w-full h-auto rounded-lg"
                    />
                    
                    {/* Навигация по кадрам */}
                    {frameHistory.length > 1 && (
                      <button
                        onClick={() => setShowHistory(!showHistory)}
                        className="absolute top-4 right-4 bg-wink-black/80 p-2 rounded-lg hover:bg-wink-black transition-colors"
                      >
                        <History className="w-5 h-5" />
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="aspect-video bg-wink-black rounded-lg flex items-center justify-center">
                    <div className="text-center">
                      <ImageIcon className="w-20 h-20 mx-auto mb-4 text-wink-gray" />
                      <p className="text-gray-400 mb-6">
                        Кадр еще не сгенерирован
                      </p>
                      <button
                        onClick={handleGenerate}
                        disabled={isGenerating}
                        className="btn-wink"
                      >
                        {isGenerating ? (
                          <>
                            <Loader2 className="w-5 h-5 animate-spin inline mr-2" />
                            Генерация...
                          </>
                        ) : (
                          <>
                            <Sparkles className="w-5 h-5 inline mr-2" />
                            Сгенерировать кадр
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}

                {/* История генераций */}
                {showHistory && frameHistory.length > 0 && (
                  <div className="mt-4 p-4 bg-wink-black rounded-lg">
                    <h3 className="font-bold mb-3 flex items-center gap-2">
                      <History className="w-4 h-4" /> История генераций
                    </h3>
                    <div className="grid grid-cols-4 gap-3">
                      {frameHistory.map((frame, index) => (
                        <button
                          key={frame.id || index}
                          onClick={() => selectHistoryFrame(frame)}
                          className={`
                            relative rounded-lg overflow-hidden border-2 transition-all
                            ${currentScene?.currentFrame?.id === frame.id 
                              ? 'border-wink-orange' 
                              : 'border-transparent hover:border-wink-gray'
                            }
                          `}
                        >
                          <img
                            src={frame.imageUrl}
                            alt={`Вариант ${index + 1}`}
                            className="w-full aspect-video object-cover"
                          />
                          <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent p-2">
                            <span className="text-xs">Вариант {index + 1}</span>
                          </div>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Навигация между сценами */}
              <div className="flex items-center justify-between mb-6">
                <button
                  onClick={() => setCurrentSceneIndex(Math.max(0, currentSceneIndex - 1))}
                  disabled={currentSceneIndex === 0}
                  className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  <ChevronLeft className="w-4 h-4" /> Предыдущая
                </button>
                
                <span className="text-gray-400">
                  Сцена {currentSceneIndex + 1} из {scenes.length}
                </span>
                
                <button
                  onClick={() => setCurrentSceneIndex(Math.min(scenes.length - 1, currentSceneIndex + 1))}
                  disabled={currentSceneIndex === scenes.length - 1}
                  className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  Следующая <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </main>

        {/* Правая панель - параметры */}
        <aside className="w-96 bg-wink-dark border-l border-wink-gray overflow-y-auto">
          <div className="p-6 space-y-6">
            {/* Уровень детализации */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-bold">Уровень детализации</h3>
                {generationPath && (
                  <span className={`text-xs px-2 py-1 rounded ${
                    generationPath === 'progressive' 
                      ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30' 
                      : 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
                  }`}>
                    {generationPath === 'progressive' ? 'Прогрессивный' : 'Прямой'}
                  </span>
                )}
              </div>

              {/* Чекбокс "Сразу финал" */}
              <div className="mb-4">
                <label className="flex items-center gap-2 cursor-pointer p-2 rounded-lg hover:bg-wink-black transition-colors">
                  <input
                    type="checkbox"
                    checked={directFinal}
                    onChange={(e) => {
                      const checked = e.target.checked;
                      setDirectFinal(checked);
                      if (checked) {
                        // При включении "Сразу финал" блокируем выбор LOD
                        setDetailLevel('final');
                        setGenerationPath('direct');
                      } else {
                        // При выключении возвращаемся к обычному режиму
                        setDetailLevel('sketch');
                        setGenerationPath(null);
                      }
                    }}
                    className="w-4 h-4 rounded border-wink-gray text-wink-orange focus:ring-wink-orange focus:ring-2"
                  />
                  <span className="text-sm font-medium">Сразу финал</span>
                </label>
                {directFinal && (
                  <p className="text-xs text-gray-400 mt-1 ml-6">
                    Пропустить эскиз и среднюю детализацию
                  </p>
                )}
              </div>

              {/* Segmented control для LOD */}
              <div className={`grid grid-cols-3 gap-2 ${directFinal ? 'opacity-50 pointer-events-none' : ''}`}>
                {DETAIL_LEVELS.map((level) => {
                  const isSelected = detailLevel === level.id;
                  const needsSketch = (level.id === 'mid' || level.id === 'final') && !hasSketchFrame();
                  
                  return (
                    <button
                      key={level.id}
                      onClick={() => {
                        if (directFinal) return;
                        setDetailLevel(level.id);
                        setGenerationPath(null);
                      }}
                      disabled={directFinal}
                      className={`
                        relative p-3 rounded-lg border-2 transition-all text-center
                        ${isSelected 
                          ? 'border-wink-orange bg-wink-orange/10' 
                          : 'border-wink-gray hover:border-wink-orange/50 bg-wink-black'
                        }
                        ${directFinal ? 'cursor-not-allowed' : 'cursor-pointer'}
                      `}
                      title={needsSketch ? 'Рекомендуется сначала создать эскиз' : ''}
                    >
                      <div className="flex flex-col items-center gap-1">
                        <span className="text-xl">{level.icon}</span>
                        <span className="text-xs font-bold">{level.name}</span>
                      </div>
                      {needsSketch && !isSelected && (
                        <div className="absolute -top-1 -right-1">
                          <AlertTriangle className="w-3 h-3 text-yellow-500" />
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>

              {/* Предупреждение при выборе Mid/Final без Sketch */}
              {(detailLevel === 'mid' || detailLevel === 'final') && !hasSketchFrame() && !directFinal && (
                <div className="mt-3 p-3 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                  <div className="flex items-start gap-2">
                    <AlertTriangle className="w-4 h-4 text-yellow-500 flex-shrink-0 mt-0.5" />
                    <div className="text-xs text-yellow-400">
                      <div className="font-bold mb-1">Рекомендация</div>
                      <div>
                        Для лучшего результата рекомендуется сначала создать эскиз (Sketch), 
                        затем использовать прогрессивную генерацию для {detailLevel === 'mid' ? 'средней детализации' : 'финального кадра'}.
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Промпт */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-bold">Промпт для генерации</h3>
                {!editingPrompt && (
                  <button
                    onClick={startEditingPrompt}
                    className="text-wink-orange hover:text-wink-orange-light transition-colors"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                )}
              </div>

              {editingPrompt ? (
                <div className="space-y-3">
                  <textarea
                    value={promptText}
                    onChange={(e) => setPromptText(e.target.value)}
                    className="input-wink w-full h-40 resize-none"
                    placeholder="Опишите, как должен выглядеть кадр..."
                  />
                  <div className="flex gap-2">
                    <button
                      onClick={() => setEditingPrompt(false)}
                      className="flex-1 px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center justify-center gap-2"
                    >
                      <X className="w-4 h-4" /> Отмена
                    </button>
                    <button
                      onClick={handleSavePrompt}
                      className="flex-1 btn-wink flex items-center justify-center gap-2"
                    >
                      <Save className="w-4 h-4" /> Сохранить
                    </button>
                  </div>
                </div>
              ) : (
                <div className="p-4 bg-wink-black rounded-lg text-sm text-gray-300">
                  {currentScene?.prompt || generateDefaultPrompt() || 'Промпт не задан'}
                </div>
              )}
            </div>

            {/* Параметры сцены */}
            <div>
              <h3 className="font-bold mb-3">Параметры сцены</h3>
              <div className="space-y-3 text-sm">
                {currentScene?.location && (
                  <div>
                    <span className="text-gray-400">Локация:</span>
                    <div className="mt-1 p-2 bg-wink-black rounded">{currentScene.location}</div>
                  </div>
                )}
                
                {currentScene?.characters?.length > 0 && (
                  <div>
                    <span className="text-gray-400">Персонажи:</span>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {currentScene.characters.map((char, i) => (
                        <span key={i} className="px-2 py-1 bg-wink-black rounded text-xs">
                          {char}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
                
                {currentScene?.props?.length > 0 && (
                  <div>
                    <span className="text-gray-400">Реквизит:</span>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {currentScene.props.map((prop, i) => (
                        <span key={i} className="px-2 py-1 bg-wink-black rounded text-xs">
                          {prop}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Слоты сцены из enriched JSON */}
            {editingSlots ? (
              <div className="border-t border-wink-gray pt-4">
                <PromptSlotsEditor
                  sceneId={currentScene?.id}
                  initialSlots={visualSlots}
                  onSave={handleSaveSlots}
                  onCancel={() => setEditingSlots(false)}
                  onPreviewChange={setPreviewPrompt}
                />
              </div>
            ) : (
              visualSlots && (
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-bold">Промпт-слоты</h3>
                    <button
                      onClick={handleEditSlots}
                      className="text-wink-orange hover:text-wink-orange-light transition-colors flex items-center gap-1 text-sm"
                    >
                      <Edit3 className="w-4 h-4" />
                      Редактировать
                    </button>
                  </div>
                  
                  {/* Предпросмотр промпта, если есть */}
                  {previewPrompt && (
                    <div className="mb-3 p-3 bg-wink-black rounded-lg border border-wink-gray">
                      <div className="text-xs font-bold text-gray-400 mb-1">Предпросмотр промпта:</div>
                      <div className="text-xs text-gray-300 whitespace-pre-wrap">{previewPrompt}</div>
                    </div>
                  )}

                  {/* Отображение слотов в новом формате */}
                  <div className="space-y-3 text-xs">
                    {/* КТО: Персонажи */}
                    {visualSlots.characters && visualSlots.characters.length > 0 && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Users className="w-3 h-3" /> КТО: Персонажи
                        </div>
                        <div className="space-y-2">
                          {visualSlots.characters.map((char, idx) => (
                            <div key={idx} className="text-gray-300 pl-3 border-l-2 border-wink-gray">
                              {char.name && <div className="font-bold">{char.name}</div>}
                              {char.appearance && <div>Внешность: {char.appearance}</div>}
                              {char.clothing && char.clothing.length > 0 && (
                                <div>Одежда: {char.clothing.join(', ')}</div>
                              )}
                              {char.pose && <div>Поза: {char.pose}</div>}
                              {char.action && <div>Действие: {char.action}</div>}
                              {char.positionInFrame && <div>Позиция: {char.positionInFrame}</div>}
                              {char.emotion && <div>Эмоция: {char.emotion}</div>}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* ГДЕ: Локация */}
                    {visualSlots.location && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <MapPin className="w-3 h-3" /> ГДЕ: Локация
                        </div>
                        <div className="text-gray-300 space-y-1">
                          {visualSlots.location.sceneType && (
                            <div>Тип: {visualSlots.location.sceneType}</div>
                          )}
                          {visualSlots.location.raw && <div>Локация: {visualSlots.location.raw}</div>}
                          {visualSlots.location.description && (
                            <div>Описание: {visualSlots.location.description}</div>
                          )}
                          {visualSlots.location.environmentDetails && visualSlots.location.environmentDetails.length > 0 && (
                            <div>Детали: {visualSlots.location.environmentDetails.join(', ')}</div>
                          )}
                          {visualSlots.location.time?.description && (
                            <div>Время: {visualSlots.location.time.description}</div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* ЧТО: Действие и реквизиты */}
                    {visualSlots.action && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Zap className="w-3 h-3" /> ЧТО: Действие
                        </div>
                        <div className="text-gray-300 space-y-1">
                          {visualSlots.action.mainAction && (
                            <div>Действие: {visualSlots.action.mainAction}</div>
                          )}
                          {visualSlots.action.props && visualSlots.action.props.length > 0 && (
                            <div>
                              Реквизиты:{' '}
                              {visualSlots.action.props.map((p, i) => (
                                <span key={i} className="mr-2">
                                  {p.name}
                                  {p.required && <span className="text-red-400">*</span>}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* КОМПОЗИЦИЯ */}
                    {visualSlots.composition && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Camera className="w-3 h-3" /> КОМПОЗИЦИЯ
                        </div>
                        <div className="text-gray-300 space-y-1">
                          {visualSlots.composition.shotType && (
                            <div>Тип кадра: {visualSlots.composition.shotType}</div>
                          )}
                          {visualSlots.composition.cameraAngle && (
                            <div>Угол: {visualSlots.composition.cameraAngle}</div>
                          )}
                          {visualSlots.composition.framing && (
                            <div>Композиция: {visualSlots.composition.framing}</div>
                          )}
                          {visualSlots.composition.motion && (
                            <div>Движение: {visualSlots.composition.motion}</div>
                          )}
                          {visualSlots.composition.locationalCues && visualSlots.composition.locationalCues.length > 0 && (
                            <div>Подсказки: {visualSlots.composition.locationalCues.join(', ')}</div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* ТОН */}
                    {visualSlots.tone && visualSlots.tone.length > 0 && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Palette className="w-3 h-3" /> ТОН
                        </div>
                        <div className="text-gray-300 flex flex-wrap gap-1">
                          {visualSlots.tone.map((tone, idx) => (
                            <span key={idx} className="px-2 py-0.5 bg-wink-dark rounded text-xs">
                              {tone}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* СТИЛЬ */}
                    {visualSlots.styleHints && visualSlots.styleHints.length > 0 && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Brush className="w-3 h-3" /> СТИЛЬ
                        </div>
                        <div className="text-gray-300 flex flex-wrap gap-1">
                          {visualSlots.styleHints.map((style, idx) => (
                            <span key={idx} className="px-2 py-0.5 bg-wink-dark rounded text-xs">
                              {style}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Негативы */}
                    {visualSlots.negatives && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-2 flex items-center gap-1">
                          <Ban className="w-3 h-3" /> Негативы
                        </div>
                        <div className="text-gray-300 space-y-1 text-xs">
                          {visualSlots.negatives.global && visualSlots.negatives.global.length > 0 && (
                            <div>
                              Глобальные: {visualSlots.negatives.global.join(', ')}
                            </div>
                          )}
                          {visualSlots.negatives.sceneSpecific && visualSlots.negatives.sceneSpecific.length > 0 && (
                            <div>
                              Сценовые: {visualSlots.negatives.sceneSpecific.join(', ')}
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* Legacy поля для обратной совместимости */}
                    {visualSlots.lighting && (
                      <div className="bg-wink-black rounded-lg p-3">
                        <div className="font-bold text-gray-400 mb-1">Освещение</div>
                        <div className="text-gray-300 text-xs">{visualSlots.lighting}</div>
                      </div>
                    )}
                  </div>
                </div>
              )
            )}

            {/* Галерея кадров по всему сценарию */}
            {showGallery && (
              <div className="mt-6 p-4 bg-wink-dark rounded-lg border border-wink-gray/60">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-bold flex items-center gap-2">
                    <FileImage className="w-4 h-4 text-wink-orange" />
                    Галерея кадров сценария
                  </h3>
                  <div className="flex items-center gap-3">
                    <select
                      value={galleryLodFilter}
                      onChange={(e) => setGalleryLodFilter(e.target.value)}
                      className="bg-wink-black border border-wink-gray text-xs rounded px-2 py-1"
                    >
                      <option value="all">Все LOD</option>
                      <option value="sketch">Эскиз</option>
                      <option value="mid">Средняя</option>
                      <option value="final">Финальный</option>
                      <option value="direct_final">Сразу финал</option>
                    </select>
                    <button
                      onClick={() => setShowGallery(false)}
                      className="text-xs text-gray-400 hover:text-white"
                    >
                      Закрыть
                    </button>
                  </div>
                </div>

                {filteredGalleryCards.length === 0 ? (
                  <p className="text-xs text-gray-400">
                    Кадров для выбранного фильтра пока нет.
                  </p>
                ) : (
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {filteredGalleryCards.map((card) => (
                      <div
                        key={card.frameId}
                        className="bg-wink-black rounded-lg overflow-hidden border border-wink-gray/60"
                      >
                        {card.imageUrl && (
                          <a
                            href={card.imageUrl}
                            target="_blank"
                            rel="noreferrer"
                          >
                            <img
                              src={card.imageUrl}
                              alt={card.sceneTitle || 'Кадр'}
                              className="w-full aspect-video object-cover"
                            />
                          </a>
                        )}
                        <div className="p-2 space-y-1">
                          <div className="text-[11px] font-bold truncate">
                            {card.sceneTitle || 'Без названия'}
                          </div>
                          <div className="flex flex-wrap gap-1 text-[10px] text-gray-400">
                            {card.lod && (
                              <span className="px-1.5 py-0.5 bg-wink-dark rounded border border-wink-gray">
                                LOD: {card.lod}
                              </span>
                            )}
                            {card.path && (
                              <span className="px-1.5 py-0.5 bg-wink-dark rounded border border-wink-gray">
                                {card.path}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Технические параметры кадра */}
            {currentScene?.currentFrame?.meta && (
              <div>
                <h3 className="font-bold mb-3">Технические параметры</h3>
                <div className="space-y-2 text-xs text-gray-300 bg-wink-black rounded-lg p-3">
                  <div className="flex flex-wrap gap-2">
                    {currentScene.currentFrame.detailLevel && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        LOD: {currentScene.currentFrame.detailLevel === 'medium' ? 'mid' : currentScene.currentFrame.detailLevel}
                      </span>
                    )}
                    {(currentScene.currentFrame.path || currentScene.currentFrame.generationPath) && (
                      <span className={`px-2 py-1 bg-wink-dark rounded border ${
                        (currentScene.currentFrame.path || currentScene.currentFrame.generationPath) === 'progressive'
                          ? 'border-blue-500/50 text-blue-400'
                          : 'border-purple-500/50 text-purple-400'
                      }`}>
                        Путь: {(currentScene.currentFrame.path || currentScene.currentFrame.generationPath) === 'progressive' ? 'Прогрессивный' : 'Прямой'}
                      </span>
                    )}
                    {typeof currentScene.currentFrame.meta.seed === 'number' && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        Seed: {currentScene.currentFrame.meta.seed}
                      </span>
                    )}
                    {typeof currentScene.currentFrame.meta.steps === 'number' && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        Steps: {currentScene.currentFrame.meta.steps}
                      </span>
                    )}
                    {typeof currentScene.currentFrame.meta.cfg === 'number' && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        CFG: {currentScene.currentFrame.meta.cfg}
                      </span>
                    )}
                    {currentScene.currentFrame.meta.sampler && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        Sampler: {currentScene.currentFrame.meta.sampler}
                      </span>
                    )}
                    {currentScene.currentFrame.meta.scheduler && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        Scheduler: {currentScene.currentFrame.meta.scheduler}
                      </span>
                    )}
                    {currentScene.currentFrame.meta.resolution && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        Res: {currentScene.currentFrame.meta.resolution}
                      </span>
                    )}
                    {currentScene.currentFrame.meta.vae && (
                      <span className="px-2 py-1 bg-wink-dark rounded border border-wink-gray">
                        VAE: {currentScene.currentFrame.meta.vae}
                      </span>
                    )}
                  </div>
                  {currentScene.currentFrame.meta.style?.preset && (
                    <div className="mt-2">
                      <span className="font-bold text-gray-400">Стиль (preset): </span>
                      <span>{currentScene.currentFrame.meta.style.preset}</span>
                    </div>
                  )}
                  {currentScene.currentFrame.meta.style?.negatives && (
                    <div className="mt-1">
                      <span className="font-bold text-gray-400">Негативы: </span>
                      <span>{currentScene.currentFrame.meta.style.negatives}</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Действия */}
            {currentScene?.currentFrame && (
              <div className="space-y-2">
                <button
                  onClick={handleRegenerate}
                  disabled={isGenerating || !promptText.trim()}
                  className="w-full btn-wink flex items-center justify-center gap-2"
                >
                  {isGenerating ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Генерация...
                    </>
                  ) : (
                    <>
                      <RefreshCw className="w-4 h-4" />
                      Регенерировать
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
};

export default FrameViewer;

