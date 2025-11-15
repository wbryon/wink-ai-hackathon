import { useState, useEffect } from 'react';
import { 
  ChevronDown, 
  ChevronUp, 
  MapPin, 
  Users, 
  Package, 
  Edit2, 
  Trash2, 
  Plus,
  Save,
  X
} from 'lucide-react';
import { updateScene, deleteScene, addScene, generateFrame, refineScene, enrichScene, getScenes } from '../api/apiClient';
import { Sparkles } from 'lucide-react';

const SceneList = ({ scenes: initialScenes, scriptId, onContinue }) => {
  const [scenes, setScenes] = useState(initialScenes || []);
  const [expandedScenes, setExpandedScenes] = useState(new Set([0])); // Первая сцена открыта по умолчанию
  const [editingScene, setEditingScene] = useState(null);
  const [editFormData, setEditFormData] = useState({});
  const [generating, setGenerating] = useState({}); // per-scene loading flags
  const [refineText, setRefineText] = useState({}); // per-scene quick edit text
  const [refining, setRefining] = useState({}); // per-scene refine loading
  const [enriching, setEnriching] = useState({}); // per-scene enrichment loading
  const [showSceneText, setShowSceneText] = useState({}); // per-scene text visibility
  const [scenesLoading, setScenesLoading] = useState(false);

  // Загрузить сцены из API, если они не переданы или пустые
  useEffect(() => {
    const loadScenes = async () => {
      if (!scriptId) return;
      
      // Если сцены уже переданы и не пустые, не загружаем повторно
      if (initialScenes && initialScenes.length > 0) {
        return;
      }
      
      // Если сцены пустые, загружаем из API
      if (scenes.length === 0) {
        setScenesLoading(true);
        try {
          const loadedScenes = await getScenes(scriptId);
          if (loadedScenes && loadedScenes.length > 0) {
            setScenes(loadedScenes);
          }
        } catch (error) {
          console.error('Error loading scenes:', error);
        } finally {
          setScenesLoading(false);
        }
      }
    };

    loadScenes();
  }, [scriptId, initialScenes, scenes.length]);

  // Переключение раскрытия сцены
  const toggleScene = (index) => {
    const newExpanded = new Set(expandedScenes);
    if (newExpanded.has(index)) {
      newExpanded.delete(index);
    } else {
      newExpanded.add(index);
    }
    setExpandedScenes(newExpanded);
  };

  // Начать редактирование сцены
  const startEditing = (scene, index) => {
    setEditingScene(index);
    setEditFormData({
      title: scene.title || `Сцена ${index + 1}`,
      location: scene.location || '',
      characters: Array.isArray(scene.characters) ? scene.characters.join(', ') : '',
      props: Array.isArray(scene.props) ? scene.props.join(', ') : '',
      description: scene.description || '',
    });
  };

  // Сохранить изменения сцены
  const saveScene = async (sceneId, index) => {
    try {
      const updatedData = {
        ...scenes[index],
        title: editFormData.title,
        location: editFormData.location,
        characters: editFormData.characters.split(',').map(s => s.trim()).filter(Boolean),
        props: editFormData.props.split(',').map(s => s.trim()).filter(Boolean),
        description: editFormData.description,
      };

      await updateScene(sceneId, updatedData);
      
      const newScenes = [...scenes];
      newScenes[index] = updatedData;
      setScenes(newScenes);
      setEditingScene(null);
    } catch (error) {
      console.error('Error updating scene:', error);
      alert('Ошибка при сохранении сцены');
    }
  };

  // Отменить редактирование
  const cancelEditing = () => {
    setEditingScene(null);
    setEditFormData({});
  };

  // Удалить сцену
  const handleDeleteScene = async (sceneId, index) => {
    if (!confirm('Вы уверены, что хотите удалить эту сцену?')) return;

    try {
      await deleteScene(sceneId);
      const newScenes = scenes.filter((_, i) => i !== index);
      setScenes(newScenes);
    } catch (error) {
      console.error('Error deleting scene:', error);
      alert('Ошибка при удалении сцены');
    }
  };

  // Добавить новую сцену
  const handleAddScene = async () => {
    try {
      const newSceneData = {
        title: `Сцена ${scenes.length + 1}`,
        location: '',
        characters: [],
        props: [],
        description: '',
      };

      const response = await addScene(scriptId, newSceneData);
      setScenes([...scenes, response]);
      setExpandedScenes(new Set([...expandedScenes, scenes.length]));
    } catch (error) {
      console.error('Error adding scene:', error);
      alert('Ошибка при добавлении сцены');
    }
  };

  // Сгенерировать кадр для сцены
  const handleGenerateFrame = async (sceneId, index) => {
    try {
      setGenerating(prev => ({ ...prev, [index]: true }));
      // backend ожидает detailLevel: sketch | mid | final | direct_final
      const frame = await generateFrame(sceneId, 'mid');
      const newScenes = [...scenes];
      const prevFrames = Array.isArray(newScenes[index].generatedFrames) ? newScenes[index].generatedFrames : [];
      newScenes[index] = {
        ...newScenes[index],
        currentFrame: frame,
        generatedFrames: [...prevFrames, frame],
      };
      setScenes(newScenes);
    } catch (error) {
      console.error('Error generating frame:', error);
      alert('Ошибка при генерации кадра');
    } finally {
      setGenerating(prev => ({ ...prev, [index]: false }));
    }
  };

  // Быстрая правка сцены коротким текстом
  const handleRefineScene = async (sceneId, index) => {
    const instruction = (refineText[index] || '').trim();
    if (!instruction) return;

    try {
      setRefining(prev => ({ ...prev, [index]: true }));
      const updated = await refineScene(sceneId, instruction);
      const newScenes = [...scenes];
      newScenes[index] = updated;
      setScenes(newScenes);
      setRefineText(prev => ({ ...prev, [index]: '' }));
    } catch (error) {
      console.error('Error refining scene:', error);
      alert('Ошибка при применении правки. Попробуйте ещё раз.');
    } finally {
      setRefining(prev => ({ ...prev, [index]: false }));
    }
  };

  // Запуск пайплайна обогащения сцены
  const handleEnrichScene = async (sceneId, index) => {
    try {
      setEnriching(prev => ({ ...prev, [index]: true }));
      const result = await enrichScene(sceneId);
      if (result.success) {
        alert('Пайплайн обогащения успешно завершен! Enriched JSON и prompt готовы для генерации.');
      } else {
        alert('Ошибка при запуске пайплайна обогащения: ' + (result.error || 'Неизвестная ошибка'));
      }
    } catch (error) {
      console.error('Error enriching scene:', error);
      alert('Ошибка при запуске пайплайна обогащения. Попробуйте ещё раз.');
    } finally {
      setEnriching(prev => ({ ...prev, [index]: false }));
    }
  };

  return (
    <div className="min-h-screen p-6 animate-fadeInUp">
      <div className="max-w-6xl mx-auto">
        {/* Заголовок */}
        <div className="mb-8 text-center">
          <img 
            src="/images/wink-logo.webp" 
            alt="Wink Logo" 
            className="h-12 mx-auto mb-4 filter brightness-0 invert"
          />
          <h1 className="text-3xl md:text-4xl font-cofo-black mb-2 text-gradient-wink">
            Проверка сцен
          </h1>
          <p className="text-gray-400">
            Проверьте и отредактируйте извлеченные сцены перед генерацией
          </p>
        </div>

        {/* Список сцен */}
        {scenesLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-wink-orange mx-auto mb-4"></div>
              <p className="text-gray-400">Загрузка сцен...</p>
            </div>
          </div>
        ) : scenes.length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <p className="text-lg mb-2">Сцены не найдены</p>
            {scriptId && (
              <p className="text-sm">Проверьте, что сценарий обработан</p>
            )}
          </div>
        ) : (
          <div className="space-y-4 mb-6">
            {scenes.map((scene, index) => {
            const isExpanded = expandedScenes.has(index);
            const isEditing = editingScene === index;

            return (
              <div
                key={scene.id || index}
                className="card-wink"
              >
                {/* Заголовок сцены */}
                <div
                  className="flex items-center justify-between cursor-pointer"
                  onClick={() => !isEditing && toggleScene(index)}
                >
                  <div className="flex items-center gap-4">
                    <div className="bg-wink-gradient text-wink-black font-bold w-10 h-10 rounded-full flex items-center justify-center">
                      {index + 1}
                    </div>
                    <div>
                      <h3 className="text-lg font-bold">
                        {scene.title || `Сцена ${index + 1}`}
                      </h3>
                      {scene.location && (
                        <p className="text-sm text-gray-400 flex items-center gap-1">
                          <MapPin className="w-4 h-4" />
                          {scene.location}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {!isEditing && (
                      <>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            startEditing(scene, index);
                          }}
                          className="p-2 hover:bg-wink-gray rounded-lg transition-colors"
                        >
                          <Edit2 className="w-5 h-5" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteScene(scene.id, index);
                          }}
                          className="p-2 hover:bg-red-500/20 hover:text-red-500 rounded-lg transition-colors"
                        >
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </>
                    )}
                    {!isEditing && (
                      isExpanded ? (
                        <ChevronUp className="w-6 h-6 text-wink-orange" />
                      ) : (
                        <ChevronDown className="w-6 h-6" />
                      )
                    )}
                  </div>
                </div>

                {/* Содержимое сцены */}
                {isExpanded && (
                  <div className="mt-4 pt-4 border-t border-wink-gray space-y-4">
                    {isEditing ? (
                      // Режим редактирования
                      <div className="space-y-4">
                        <div>
                          <label className="block text-sm font-bold mb-2">
                            Название сцены
                          </label>
                          <input
                            type="text"
                            value={editFormData.title}
                            onChange={(e) => setEditFormData({
                              ...editFormData,
                              title: e.target.value
                            })}
                            className="input-wink w-full"
                          />
                        </div>

                        <div>
                          <label className="block text-sm font-bold mb-2 flex items-center gap-2">
                            <MapPin className="w-4 h-4" /> Локация
                          </label>
                          <input
                            type="text"
                            value={editFormData.location}
                            onChange={(e) => setEditFormData({
                              ...editFormData,
                              location: e.target.value
                            })}
                            className="input-wink w-full"
                            placeholder="Например: Кафе, день"
                          />
                        </div>

                        <div>
                          <label className="block text-sm font-bold mb-2 flex items-center gap-2">
                            <Users className="w-4 h-4" /> Персонажи
                          </label>
                          <input
                            type="text"
                            value={editFormData.characters}
                            onChange={(e) => setEditFormData({
                              ...editFormData,
                              characters: e.target.value
                            })}
                            className="input-wink w-full"
                            placeholder="Через запятую: Анна, Борис, Виктор"
                          />
                        </div>

                        <div>
                          <label className="block text-sm font-bold mb-2 flex items-center gap-2">
                            <Package className="w-4 h-4" /> Реквизит
                          </label>
                          <input
                            type="text"
                            value={editFormData.props}
                            onChange={(e) => setEditFormData({
                              ...editFormData,
                              props: e.target.value
                            })}
                            className="input-wink w-full"
                            placeholder="Через запятую: Телефон, Чашка кофе"
                          />
                        </div>

                        <div>
                          <label className="block text-sm font-bold mb-2">
                            Описание сцены
                          </label>
                          <textarea
                            value={editFormData.description}
                            onChange={(e) => setEditFormData({
                              ...editFormData,
                              description: e.target.value
                            })}
                            className="input-wink w-full h-32 resize-none"
                            placeholder="Краткое описание происходящего в сцене"
                          />
                        </div>

                        <div className="flex gap-3 justify-end">
                          <button
                            onClick={cancelEditing}
                            className="px-4 py-2 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2"
                          >
                            <X className="w-4 h-4" /> Отмена
                          </button>
                          <button
                            onClick={() => saveScene(scene.id, index)}
                            className="btn-wink flex items-center gap-2"
                          >
                            <Save className="w-4 h-4" /> Сохранить
                          </button>
                        </div>
                      </div>
                    ) : (
                      // Режим просмотра
                      <div className="space-y-3">
                        {scene.location && (
                          <div className="flex items-start gap-2">
                            <MapPin className="w-5 h-5 text-wink-orange flex-shrink-0 mt-0.5" />
                            <div>
                              <span className="font-bold">Локация: </span>
                              <span className="text-gray-300">{scene.location}</span>
                            </div>
                          </div>
                        )}

                        {scene.characters && scene.characters.length > 0 && (
                          <div className="flex items-start gap-2">
                            <Users className="w-5 h-5 text-wink-orange flex-shrink-0 mt-0.5" />
                            <div>
                              <span className="font-bold">Персонажи: </span>
                              <div className="flex flex-wrap gap-2 mt-1">
                                {scene.characters.map((char, i) => (
                                  <span
                                    key={i}
                                    className="px-3 py-1 bg-wink-gray rounded-full text-sm"
                                  >
                                    {char}
                                  </span>
                                ))}
                              </div>
                            </div>
                          </div>
                        )}

                        {scene.props && scene.props.length > 0 && (
                          <div className="flex items-start gap-2">
                            <Package className="w-5 h-5 text-wink-orange flex-shrink-0 mt-0.5" />
                            <div>
                              <span className="font-bold">Реквизит: </span>
                              <div className="flex flex-wrap gap-2 mt-1">
                                {scene.props.map((prop, i) => (
                                  <span
                                    key={i}
                                    className="px-3 py-1 bg-wink-gray rounded-full text-sm"
                                  >
                                    {prop}
                                  </span>
                                ))}
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Текст сцены */}
                        {scene.description && (
                          <div className="mt-3">
                            <div className="flex items-center justify-between mb-2">
                              <label className="block text-sm font-bold text-gray-400">
                                Текст сцены
                              </label>
                              <button
                                onClick={() => setShowSceneText(prev => ({
                                  ...prev,
                                  [index]: !prev[index]
                                }))}
                                className="text-xs text-wink-orange hover:text-wink-orange/80 transition-colors"
                              >
                                {showSceneText[index] ? 'Скрыть' : 'Показать'}
                              </button>
                            </div>
                            {showSceneText[index] && (
                              <div className="p-4 bg-wink-black/50 rounded-lg max-h-96 overflow-y-auto">
                                <pre className="text-gray-300 leading-relaxed whitespace-pre-wrap font-mono text-xs">
                                  {scene.description}
                                </pre>
                              </div>
                            )}
                          </div>
                        )}

                        {/* Быстрые действия по сцене */}
                        <div className="pt-4 space-y-3">
                          {/* Запуск пайплайна обогащения */}
                          <div>
                            <button
                              onClick={() => handleEnrichScene(scene.id, index)}
                              disabled={!!enriching[index]}
                              className="w-full btn-wink flex items-center justify-center gap-2"
                            >
                              <Sparkles className="w-4 h-4" />
                              {enriching[index] ? 'Запуск пайплайна...' : 'Запустить пайплайн обогащения'}
                            </button>
                            <p className="text-xs text-gray-500 mt-1 text-center">
                              scene text → ollama → json → ollama → enriched json → ollama → prompt
                            </p>
                          </div>

                          {/* Быстрая текстовая правка */}
                          <div>
                            <label className="block text-xs font-bold mb-1 text-gray-400">
                              Короткая правка сцены
                            </label>
                            <textarea
                              value={refineText[index] || ''}
                              onChange={(e) =>
                                setRefineText(prev => ({ ...prev, [index]: e.target.value }))
                              }
                              className="input-wink w-full h-20 resize-none text-xs"
                              placeholder="Например: сделать атмосферу более мрачной и добавить дождь за окном"
                            />
                            <div className="flex justify-end mt-2">
                              <button
                                onClick={() => handleRefineScene(scene.id, index)}
                                disabled={refining[index] || !(refineText[index] || '').trim()}
                                className="px-4 py-2 text-xs border border-wink-orange text-wink-orange rounded-lg hover:bg-wink-orange hover:text-wink-black transition-colors"
                              >
                                {refining[index] ? 'Применение...' : 'Применить правку'}
                              </button>
                            </div>
                          </div>

                          {/* Генерация кадра */}
                          <div>
                            <button
                              onClick={() => handleGenerateFrame(scene.id, index)}
                              disabled={!!generating[index]}
                              className="btn-wink"
                            >
                              {generating[index] ? 'Генерация...' : 'Сгенерировать кадр'}
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
          </div>
        )}

        {/* Кнопки действий */}
        <div className="flex gap-4 justify-center">
          <button
            onClick={handleAddScene}
            className="px-6 py-3 border border-wink-orange text-wink-orange rounded-lg hover:bg-wink-orange hover:text-wink-black transition-all flex items-center gap-2"
          >
            <Plus className="w-5 h-5" /> Добавить сцену
          </button>
          
          <button
            onClick={() => onContinue(scenes)}
            className="btn-wink text-lg px-8"
          >
            Продолжить к генерации
          </button>
        </div>

        {/* Статистика */}
        <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-4 text-center">
          <div className="card-wink">
            <div className="text-3xl font-cofo-black text-wink-orange mb-1">
              {scenes.length}
            </div>
            <p className="text-sm text-gray-400">Всего сцен</p>
          </div>
          <div className="card-wink">
            <div className="text-3xl font-cofo-black text-wink-orange mb-1">
              {new Set(scenes.flatMap(s => s.characters || [])).size}
            </div>
            <p className="text-sm text-gray-400">Персонажей</p>
          </div>
          <div className="card-wink">
            <div className="text-3xl font-cofo-black text-wink-orange mb-1">
              {new Set(scenes.map(s => s.location).filter(Boolean)).size}
            </div>
            <p className="text-sm text-gray-400">Локаций</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SceneList;

