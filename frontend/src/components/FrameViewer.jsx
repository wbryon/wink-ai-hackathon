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
  Upload as UploadIcon
} from 'lucide-react';
import { 
  generateFrame, 
  regenerateFrame, 
  getFrameHistory, 
  exportStoryboard,
  updateScene 
} from '../api/apiClient';

const DETAIL_LEVELS = [
  { id: 'sketch', name: 'Эскиз', icon: '🖊️', description: 'Черно-белый набросок' },
  { id: 'medium', name: 'Средняя детализация', icon: '🎨', description: 'Цветное изображение' },
  { id: 'final', name: 'Финальный кадр', icon: '✨', description: 'Детализированный стиль' },
];

const FrameViewer = ({ scenes: initialScenes, scriptId }) => {
  const [scenes, setScenes] = useState(initialScenes || []);
  const [currentSceneIndex, setCurrentSceneIndex] = useState(0);
  const [detailLevel, setDetailLevel] = useState('medium');
  const [isGenerating, setIsGenerating] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState(false);
  const [promptText, setPromptText] = useState('');
  const [frameHistory, setFrameHistory] = useState([]);
  const [showHistory, setShowHistory] = useState(false);

  const currentScene = scenes[currentSceneIndex];

  // Загрузить историю генераций при смене сцены
  useEffect(() => {
    if (currentScene?.id) {
      loadFrameHistory();
    }
  }, [currentSceneIndex]);

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
      const response = await generateFrame(currentScene.id, detailLevel);
      
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
      const response = await regenerateFrame(
        currentScene.currentFrame.id,
        promptText,
        detailLevel
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
                          <span className={`text-xs flex items-center gap-1 ${currentSceneIndex === index ? 'text-wink-black' : 'text-green-500'}`}>
                            <Sparkles className="w-3 h-3" /> Сгенерировано
                          </span>
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
              <h3 className="font-bold mb-3">Уровень детализации</h3>
              <div className="space-y-2">
                {DETAIL_LEVELS.map((level) => (
                  <button
                    key={level.id}
                    onClick={() => setDetailLevel(level.id)}
                    className={`
                      w-full text-left p-3 rounded-lg border-2 transition-all
                      ${detailLevel === level.id 
                        ? 'border-wink-orange bg-wink-orange/10' 
                        : 'border-wink-gray hover:border-wink-orange/50'
                      }
                    `}
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-2xl">{level.icon}</span>
                      <div>
                        <div className="font-bold">{level.name}</div>
                        <div className="text-sm text-gray-400">{level.description}</div>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
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

