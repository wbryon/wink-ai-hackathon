import { useState, useEffect, useMemo } from 'react';
import {
  Save,
  X,
  Eye,
  EyeOff,
  Plus,
  Trash2,
  AlertCircle,
  CheckCircle2,
  Users,
  MapPin,
  Zap,
  Camera,
  Palette,
  Brush,
  Ban,
  Edit3,
  Loader2,
} from 'lucide-react';

/**
 * Компонент для редактирования промпт-слотов сцены.
 * Поддерживает все слоты: КТО, ГДЕ, ЧТО, КОМПОЗИЦИЯ, ТОН, СТИЛЬ, Негативы.
 */
const PromptSlotsEditor = ({ 
  sceneId, 
  initialSlots, 
  onSave, 
  onCancel,
  onPreviewChange,
}) => {
  // Состояние слотов
  const [slots, setSlots] = useState(() => initializeSlots(initialSlots));
  const [showPreview, setShowPreview] = useState(false);
  const [validationErrors, setValidationErrors] = useState({});
  const [isSaving, setIsSaving] = useState(false);

  // Инициализация слотов из initialSlots или пустых значений
  function initializeSlots(initial) {
    if (!initial) {
      return {
        characters: [],
        location: {
          raw: '',
          normalized: '',
          description: '',
          environmentDetails: [],
          sceneType: null,
          time: {
            raw: '',
            normalized: '',
            description: '',
          },
        },
        action: {
          mainAction: '',
          props: [],
        },
        composition: {
          shotType: '',
          cameraAngle: '',
          framing: '',
          motion: '',
          locationalCues: [],
        },
        tone: [],
        styleHints: [],
        negatives: {
          global: [],
          sceneSpecific: [],
        },
        lighting: '',
        technical: '',
      };
    }

    // Преобразуем legacy формат в новый
    return {
      characters: initial.characters || [],
      location: initial.location || {
        raw: '',
        normalized: '',
        description: '',
        environmentDetails: [],
        sceneType: null,
        time: {
          raw: '',
          normalized: '',
          description: '',
        },
      },
      action: initial.action || {
        mainAction: '',
        props: [],
      },
      composition: initial.composition || {
        shotType: '',
        cameraAngle: '',
        framing: '',
        motion: '',
        locationalCues: [],
      },
      tone: initial.tone || [],
      styleHints: initial.styleHints || [],
      negatives: initial.negatives || {
        global: [],
        sceneSpecific: [],
      },
      lighting: initial.lighting || '',
      technical: initial.technical || '',
    };
  }

  // Обновляем слоты при изменении initialSlots
  useEffect(() => {
    if (initialSlots) {
      setSlots(initializeSlots(initialSlots));
    }
  }, [initialSlots]);

  // Валидация обязательных слотов
  const validateSlots = () => {
    const errors = {};

    // Проверка обязательных полей
    if (!slots.location?.raw?.trim()) {
      errors.location = 'Локация обязательна';
    }

    if (!slots.action?.mainAction?.trim() && (!slots.action?.props || slots.action.props.length === 0)) {
      errors.action = 'Необходимо указать действие или реквизиты';
    }

    if (!slots.composition?.shotType?.trim()) {
      errors.composition = 'Тип кадра обязателен';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Генерация предпросмотра промпта из слотов
  const previewPrompt = useMemo(() => {
    const parts = [];

    // КТО: Персонажи
    if (slots.characters && slots.characters.length > 0) {
      const chars = slots.characters.map(ch => {
        const parts = [];
        if (ch.name) parts.push(ch.name);
        if (ch.appearance) parts.push(ch.appearance);
        if (ch.clothing && ch.clothing.length > 0) parts.push(`одежда: ${ch.clothing.join(', ')}`);
        if (ch.pose) parts.push(`поза: ${ch.pose}`);
        if (ch.action) parts.push(`действие: ${ch.action}`);
        if (ch.positionInFrame) parts.push(`позиция: ${ch.positionInFrame}`);
        if (ch.emotion) parts.push(`эмоция: ${ch.emotion}`);
        return parts.join(', ');
      });
      parts.push(`Персонажи: ${chars.join(' | ')}`);
    }

    // ГДЕ: Локация
    if (slots.location) {
      const locParts = [];
      if (slots.location.sceneType) locParts.push(slots.location.sceneType);
      if (slots.location.raw) locParts.push(slots.location.raw);
      if (slots.location.description) locParts.push(slots.location.description);
      if (slots.location.environmentDetails && slots.location.environmentDetails.length > 0) {
        locParts.push(slots.location.environmentDetails.join(', '));
      }
      if (slots.location.time?.description) {
        locParts.push(`время: ${slots.location.time.description}`);
      }
      if (locParts.length > 0) {
        parts.push(`Локация: ${locParts.join(', ')}`);
      }
    }

    // ЧТО: Действие и реквизиты
    if (slots.action) {
      const actionParts = [];
      if (slots.action.mainAction) actionParts.push(slots.action.mainAction);
      if (slots.action.props && slots.action.props.length > 0) {
        const props = slots.action.props.map(p => 
          `${p.name}${p.required ? ' (обязательно)' : ''}`
        );
        actionParts.push(`реквизит: ${props.join(', ')}`);
      }
      if (actionParts.length > 0) {
        parts.push(`Действие: ${actionParts.join('. ')}`);
      }
    }

    // КОМПОЗИЦИЯ
    if (slots.composition) {
      const compParts = [];
      if (slots.composition.shotType) compParts.push(slots.composition.shotType);
      if (slots.composition.cameraAngle) compParts.push(`угол: ${slots.composition.cameraAngle}`);
      if (slots.composition.framing) compParts.push(`композиция: ${slots.composition.framing}`);
      if (slots.composition.motion) compParts.push(`движение: ${slots.composition.motion}`);
      if (slots.composition.locationalCues && slots.composition.locationalCues.length > 0) {
        compParts.push(`подсказки: ${slots.composition.locationalCues.join(', ')}`);
      }
      if (compParts.length > 0) {
        parts.push(`Композиция: ${compParts.join(', ')}`);
      }
    }

    // ТОН
    if (slots.tone && slots.tone.length > 0) {
      parts.push(`Тон: ${slots.tone.join(', ')}`);
    }

    // СТИЛЬ
    if (slots.styleHints && slots.styleHints.length > 0) {
      parts.push(`Стиль: ${slots.styleHints.join(', ')}`);
    }

    // Освещение
    if (slots.lighting) {
      parts.push(`Освещение: ${slots.lighting}`);
    }

    return parts.join('. ');
  }, [slots]);

  // Обновляем предпросмотр при изменении слотов
  useEffect(() => {
    if (onPreviewChange) {
      onPreviewChange(previewPrompt);
    }
  }, [previewPrompt, onPreviewChange]);

  // Обработка сохранения
  const handleSave = async () => {
    if (!validateSlots()) {
      return;
    }

    setIsSaving(true);
    try {
      await onSave(slots);
    } catch (error) {
      console.error('Error saving slots:', error);
      alert('Ошибка при сохранении слотов. Попробуйте еще раз.');
    } finally {
      setIsSaving(false);
    }
  };

  // Добавление персонажа
  const addCharacter = () => {
    setSlots(prev => ({
      ...prev,
      characters: [
        ...(prev.characters || []),
        {
          name: '',
          appearance: '',
          clothing: [],
          pose: '',
          action: '',
          positionInFrame: '',
          emotion: '',
        },
      ],
    }));
  };

  // Удаление персонажа
  const removeCharacter = (index) => {
    setSlots(prev => ({
      ...prev,
      characters: prev.characters.filter((_, i) => i !== index),
    }));
  };

  // Обновление персонажа
  const updateCharacter = (index, field, value) => {
    setSlots(prev => ({
      ...prev,
      characters: prev.characters.map((ch, i) =>
        i === index ? { ...ch, [field]: value } : ch
      ),
    }));
  };

  // Добавление элемента одежды персонажу
  const addClothingItem = (charIndex, item) => {
    if (!item.trim()) return;
    setSlots(prev => ({
      ...prev,
      characters: prev.characters.map((ch, i) =>
        i === charIndex
          ? { ...ch, clothing: [...(ch.clothing || []), item.trim()] }
          : ch
      ),
    }));
  };

  // Удаление элемента одежды
  const removeClothingItem = (charIndex, itemIndex) => {
    setSlots(prev => ({
      ...prev,
      characters: prev.characters.map((ch, i) =>
        i === charIndex
          ? { ...ch, clothing: ch.clothing.filter((_, idx) => idx !== itemIndex) }
          : ch
      ),
    }));
  };

  // Добавление реквизита
  const addProp = () => {
    setSlots(prev => ({
      ...prev,
      action: {
        ...prev.action,
        props: [
          ...(prev.action?.props || []),
          { name: '', required: false, owner: '' },
        ],
      },
    }));
  };

  // Удаление реквизита
  const removeProp = (index) => {
    setSlots(prev => ({
      ...prev,
      action: {
        ...prev.action,
        props: prev.action?.props?.filter((_, i) => i !== index) || [],
      },
    }));
  };

  // Добавление тона
  const addTone = () => {
    const tone = prompt('Введите тон сцены:');
    if (tone && tone.trim()) {
      setSlots(prev => ({
        ...prev,
        tone: [...(prev.tone || []), tone.trim()],
      }));
    }
  };

  // Удаление тона
  const removeTone = (index) => {
    setSlots(prev => ({
      ...prev,
      tone: prev.tone.filter((_, i) => i !== index),
    }));
  };

  // Добавление стиля
  const addStyleHint = () => {
    const style = prompt('Введите стилистическую подсказку:');
    if (style && style.trim()) {
      setSlots(prev => ({
        ...prev,
        styleHints: [...(prev.styleHints || []), style.trim()],
      }));
    }
  };

  // Удаление стиля
  const removeStyleHint = (index) => {
    setSlots(prev => ({
      ...prev,
      styleHints: prev.styleHints.filter((_, i) => i !== index),
    }));
  };

  // Добавление локационной подсказки
  const addLocationalCue = () => {
    const cue = prompt('Введите локационную подсказку (например, "у окна"):');
    if (cue && cue.trim()) {
      setSlots(prev => ({
        ...prev,
        composition: {
          ...prev.composition,
          locationalCues: [...(prev.composition?.locationalCues || []), cue.trim()],
        },
      }));
    }
  };

  // Удаление локационной подсказки
  const removeLocationalCue = (index) => {
    setSlots(prev => ({
      ...prev,
      composition: {
        ...prev.composition,
        locationalCues: prev.composition?.locationalCues?.filter((_, i) => i !== index) || [],
      },
    }));
  };

  // Добавление детали окружения
  const addEnvironmentDetail = () => {
    const detail = prompt('Введите деталь окружения:');
    if (detail && detail.trim()) {
      setSlots(prev => ({
        ...prev,
        location: {
          ...prev.location,
          environmentDetails: [...(prev.location?.environmentDetails || []), detail.trim()],
        },
      }));
    }
  };

  // Удаление детали окружения
  const removeEnvironmentDetail = (index) => {
    setSlots(prev => ({
      ...prev,
      location: {
        ...prev.location,
        environmentDetails: prev.location?.environmentDetails?.filter((_, i) => i !== index) || [],
      },
    }));
  };

  // Добавление негатива
  const addNegative = (type) => {
    const negative = prompt(`Введите ${type === 'global' ? 'глобальный' : 'сценовый'} негатив:`);
    if (negative && negative.trim()) {
      setSlots(prev => ({
        ...prev,
        negatives: {
          ...prev.negatives,
          [type]: [...(prev.negatives?.[type] || []), negative.trim()],
        },
      }));
    }
  };

  // Удаление негатива
  const removeNegative = (type, index) => {
    setSlots(prev => ({
      ...prev,
      negatives: {
        ...prev.negatives,
        [type]: prev.negatives?.[type]?.filter((_, i) => i !== index) || [],
      },
    }));
  };

  return (
    <div className="space-y-6 max-h-[calc(100vh-200px)] overflow-y-auto">
      {/* Заголовок с кнопками */}
      <div className="flex items-center justify-between sticky top-0 bg-wink-dark pb-4 border-b border-wink-gray z-10">
        <h3 className="text-lg font-bold flex items-center gap-2">
          <Edit3 className="w-5 h-5 text-wink-orange" />
          Редактирование промпт-слотов
        </h3>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowPreview(!showPreview)}
            className="px-3 py-1.5 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2 text-sm"
          >
            {showPreview ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            {showPreview ? 'Скрыть' : 'Показать'} предпросмотр
          </button>
          <button
            onClick={onCancel}
            className="px-3 py-1.5 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors flex items-center gap-2 text-sm"
          >
            <X className="w-4 h-4" /> Отмена
          </button>
          <button
            onClick={handleSave}
            disabled={isSaving || Object.keys(validationErrors).length > 0}
            className="px-4 py-1.5 btn-wink flex items-center gap-2 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSaving ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Сохранение...
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                Сохранить
              </>
            )}
          </button>
        </div>
      </div>

      {/* Предпросмотр промпта */}
      {showPreview && (
        <div className="p-4 bg-wink-black rounded-lg border border-wink-gray">
          <div className="flex items-center gap-2 mb-2">
            <Eye className="w-4 h-4 text-wink-orange" />
            <span className="font-bold text-sm">Предпросмотр промпта:</span>
          </div>
          <p className="text-sm text-gray-300 whitespace-pre-wrap leading-relaxed">
            {previewPrompt || 'Заполните слоты для предпросмотра промпта'}
          </p>
        </div>
      )}

      {/* Ошибки валидации */}
      {Object.keys(validationErrors).length > 0 && (
        <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg">
          <div className="flex items-start gap-2">
            <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
            <div className="text-xs text-red-400">
              <div className="font-bold mb-1">Ошибки валидации:</div>
              <ul className="list-disc list-inside space-y-1">
                {Object.entries(validationErrors).map(([field, error]) => (
                  <li key={field}>{error}</li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}

      {/* КТО: Персонажи */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-bold flex items-center gap-2">
            <Users className="w-4 h-4 text-wink-orange" />
            КТО: Персонажи
          </h4>
          <button
            onClick={addCharacter}
            className="px-2 py-1 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors flex items-center gap-1"
          >
            <Plus className="w-3 h-3" /> Добавить
          </button>
        </div>

        {slots.characters && slots.characters.length > 0 ? (
          <div className="space-y-3">
            {slots.characters.map((char, index) => (
              <div key={index} className="p-3 bg-wink-black rounded-lg border border-wink-gray">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-gray-400">Персонаж {index + 1}</span>
                  <button
                    onClick={() => removeCharacter(index)}
                    className="text-red-400 hover:text-red-300"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
                <div className="space-y-2 text-sm">
                  <input
                    type="text"
                    placeholder="Имя персонажа *"
                    value={char.name || ''}
                    onChange={(e) => updateCharacter(index, 'name', e.target.value)}
                    className="input-wink w-full text-xs"
                  />
                  <textarea
                    placeholder="Внешность"
                    value={char.appearance || ''}
                    onChange={(e) => updateCharacter(index, 'appearance', e.target.value)}
                    className="input-wink w-full text-xs h-16 resize-none"
                  />
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-xs text-gray-400">Одежда:</span>
                      <input
                        type="text"
                        placeholder="Добавить элемент одежды"
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            addClothingItem(index, e.target.value);
                            e.target.value = '';
                          }
                        }}
                        className="input-wink flex-1 text-xs"
                      />
                    </div>
                    {char.clothing && char.clothing.length > 0 && (
                      <div className="flex flex-wrap gap-1 mt-1">
                        {char.clothing.map((item, itemIndex) => (
                          <span
                            key={itemIndex}
                            className="px-2 py-0.5 bg-wink-dark rounded text-xs flex items-center gap-1"
                          >
                            {item}
                            <button
                              onClick={() => removeClothingItem(index, itemIndex)}
                              className="text-red-400 hover:text-red-300"
                            >
                              <X className="w-2 h-2" />
                            </button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <input
                    type="text"
                    placeholder="Поза"
                    value={char.pose || ''}
                    onChange={(e) => updateCharacter(index, 'pose', e.target.value)}
                    className="input-wink w-full text-xs"
                  />
                  <input
                    type="text"
                    placeholder="Действие персонажа"
                    value={char.action || ''}
                    onChange={(e) => updateCharacter(index, 'action', e.target.value)}
                    className="input-wink w-full text-xs"
                  />
                  <input
                    type="text"
                    placeholder="Позиция в кадре"
                    value={char.positionInFrame || ''}
                    onChange={(e) => updateCharacter(index, 'positionInFrame', e.target.value)}
                    className="input-wink w-full text-xs"
                  />
                  <input
                    type="text"
                    placeholder="Эмоция"
                    value={char.emotion || ''}
                    onChange={(e) => updateCharacter(index, 'emotion', e.target.value)}
                    className="input-wink w-full text-xs"
                  />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 italic">Персонажи не добавлены</p>
        )}
      </div>

      {/* ГДЕ: Локация */}
      <div className="space-y-3">
        <h4 className="font-bold flex items-center gap-2">
          <MapPin className="w-4 h-4 text-wink-orange" />
          ГДЕ: Локация
        </h4>
        <div className="p-3 bg-wink-black rounded-lg border border-wink-gray space-y-2">
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Тип сцены</label>
            <select
              value={slots.location?.sceneType || ''}
              onChange={(e) =>
                setSlots(prev => ({
                  ...prev,
                  location: { ...prev.location, sceneType: e.target.value || null },
                }))
              }
              className="input-wink w-full text-xs"
            >
              <option value="">Не указано</option>
              <option value="INT">INT (Интерьер)</option>
              <option value="EXT">EXT (Экстерьер)</option>
            </select>
          </div>
          <input
            type="text"
            placeholder="Локация (raw) *"
            value={slots.location?.raw || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                location: { ...prev.location, raw: e.target.value },
              }))
            }
            className={`input-wink w-full text-xs ${
              validationErrors.location ? 'border-red-500' : ''
            }`}
          />
          <input
            type="text"
            placeholder="Нормализованная локация"
            value={slots.location?.normalized || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                location: { ...prev.location, normalized: e.target.value },
              }))
            }
            className="input-wink w-full text-xs"
          />
          <textarea
            placeholder="Описание локации"
            value={slots.location?.description || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                location: { ...prev.location, description: e.target.value },
              }))
            }
            className="input-wink w-full text-xs h-20 resize-none"
          />
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs text-gray-400">Детали окружения:</span>
              <button
                onClick={addEnvironmentDetail}
                className="px-2 py-0.5 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors"
              >
                <Plus className="w-3 h-3 inline" />
              </button>
            </div>
            {slots.location?.environmentDetails && slots.location.environmentDetails.length > 0 && (
              <div className="flex flex-wrap gap-1 mt-1">
                {slots.location.environmentDetails.map((detail, idx) => (
                  <span
                    key={idx}
                    className="px-2 py-0.5 bg-wink-dark rounded text-xs flex items-center gap-1"
                  >
                    {detail}
                    <button
                      onClick={() => removeEnvironmentDetail(idx)}
                      className="text-red-400 hover:text-red-300"
                    >
                      <X className="w-2 h-2" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className="border-t border-wink-gray pt-2">
            <label className="text-xs text-gray-400 mb-1 block">Время суток</label>
            <input
              type="text"
              placeholder="Время (raw)"
              value={slots.location?.time?.raw || ''}
              onChange={(e) =>
                setSlots(prev => ({
                  ...prev,
                  location: {
                    ...prev.location,
                    time: { ...prev.location?.time, raw: e.target.value },
                  },
                }))
              }
              className="input-wink w-full text-xs mb-2"
            />
            <input
              type="text"
              placeholder="Нормализованное время"
              value={slots.location?.time?.normalized || ''}
              onChange={(e) =>
                setSlots(prev => ({
                  ...prev,
                  location: {
                    ...prev.location,
                    time: { ...prev.location?.time, normalized: e.target.value },
                  },
                }))
              }
              className="input-wink w-full text-xs mb-2"
            />
            <textarea
              placeholder="Визуальное описание времени суток"
              value={slots.location?.time?.description || ''}
              onChange={(e) =>
                setSlots(prev => ({
                  ...prev,
                  location: {
                    ...prev.location,
                    time: { ...prev.location?.time, description: e.target.value },
                  },
                }))
              }
              className="input-wink w-full text-xs h-16 resize-none"
            />
          </div>
        </div>
      </div>

      {/* ЧТО: Действие и реквизиты */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-bold flex items-center gap-2">
            <Zap className="w-4 h-4 text-wink-orange" />
            ЧТО: Действие и реквизиты
          </h4>
          <button
            onClick={addProp}
            className="px-2 py-1 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors flex items-center gap-1"
          >
            <Plus className="w-3 h-3" /> Добавить реквизит
          </button>
        </div>
        <div className="p-3 bg-wink-black rounded-lg border border-wink-gray space-y-2">
          <textarea
            placeholder="Основное действие в сцене"
            value={slots.action?.mainAction || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                action: { ...prev.action, mainAction: e.target.value },
              }))
            }
            className="input-wink w-full text-xs h-20 resize-none"
          />
          {slots.action?.props && slots.action.props.length > 0 && (
            <div className="space-y-2">
              {slots.action.props.map((prop, index) => (
                <div key={index} className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="Название реквизита"
                    value={prop.name || ''}
                    onChange={(e) => {
                      const newProps = [...slots.action.props];
                      newProps[index] = { ...prop, name: e.target.value };
                      setSlots(prev => ({
                        ...prev,
                        action: { ...prev.action, props: newProps },
                      }));
                    }}
                    className="input-wink flex-1 text-xs"
                  />
                  <label className="flex items-center gap-1 text-xs cursor-pointer">
                    <input
                      type="checkbox"
                      checked={prop.required || false}
                      onChange={(e) => {
                        const newProps = [...slots.action.props];
                        newProps[index] = { ...prop, required: e.target.checked };
                        setSlots(prev => ({
                          ...prev,
                          action: { ...prev.action, props: newProps },
                        }));
                      }}
                      className="w-3 h-3"
                    />
                    Обязательно
                  </label>
                  <input
                    type="text"
                    placeholder="Владелец"
                    value={prop.owner || ''}
                    onChange={(e) => {
                      const newProps = [...slots.action.props];
                      newProps[index] = { ...prop, owner: e.target.value };
                      setSlots(prev => ({
                        ...prev,
                        action: { ...prev.action, props: newProps },
                      }));
                    }}
                    className="input-wink w-24 text-xs"
                  />
                  <button
                    onClick={() => removeProp(index)}
                    className="text-red-400 hover:text-red-300"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* КОМПОЗИЦИЯ */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-bold flex items-center gap-2">
            <Camera className="w-4 h-4 text-wink-orange" />
            КОМПОЗИЦИЯ
          </h4>
        </div>
        <div className="p-3 bg-wink-black rounded-lg border border-wink-gray space-y-2">
          <input
            type="text"
            placeholder="Тип кадра (shot type) *"
            value={slots.composition?.shotType || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                composition: { ...prev.composition, shotType: e.target.value },
              }))
            }
            className={`input-wink w-full text-xs ${
              validationErrors.composition ? 'border-red-500' : ''
            }`}
          />
          <input
            type="text"
            placeholder="Угол камеры"
            value={slots.composition?.cameraAngle || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                composition: { ...prev.composition, cameraAngle: e.target.value },
              }))
            }
            className="input-wink w-full text-xs"
          />
          <textarea
            placeholder="Композиция кадра (framing)"
            value={slots.composition?.framing || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                composition: { ...prev.composition, framing: e.target.value },
              }))
            }
            className="input-wink w-full text-xs h-16 resize-none"
          />
          <input
            type="text"
            placeholder="Движение в кадре"
            value={slots.composition?.motion || ''}
            onChange={(e) =>
              setSlots(prev => ({
                ...prev,
                composition: { ...prev.composition, motion: e.target.value },
              }))
            }
            className="input-wink w-full text-xs"
          />
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs text-gray-400">Локационные подсказки:</span>
              <button
                onClick={addLocationalCue}
                className="px-2 py-0.5 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors"
              >
                <Plus className="w-3 h-3 inline" />
              </button>
            </div>
            {slots.composition?.locationalCues && slots.composition.locationalCues.length > 0 && (
              <div className="flex flex-wrap gap-1 mt-1">
                {slots.composition.locationalCues.map((cue, idx) => (
                  <span
                    key={idx}
                    className="px-2 py-0.5 bg-wink-dark rounded text-xs flex items-center gap-1"
                  >
                    {cue}
                    <button
                      onClick={() => removeLocationalCue(idx)}
                      className="text-red-400 hover:text-red-300"
                    >
                      <X className="w-2 h-2" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ТОН */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-bold flex items-center gap-2">
            <Palette className="w-4 h-4 text-wink-orange" />
            ТОН
          </h4>
          <button
            onClick={addTone}
            className="px-2 py-1 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors flex items-center gap-1"
          >
            <Plus className="w-3 h-3" /> Добавить
          </button>
        </div>
        {slots.tone && slots.tone.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {slots.tone.map((tone, index) => (
              <span
                key={index}
                className="px-3 py-1 bg-wink-black rounded-lg border border-wink-gray text-sm flex items-center gap-2"
              >
                {tone}
                <button
                  onClick={() => removeTone(index)}
                  className="text-red-400 hover:text-red-300"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 italic">Тон не задан</p>
        )}
      </div>

      {/* СТИЛЬ */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-bold flex items-center gap-2">
            <Brush className="w-4 h-4 text-wink-orange" />
            СТИЛЬ
          </h4>
          <button
            onClick={addStyleHint}
            className="px-2 py-1 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors flex items-center gap-1"
          >
            <Plus className="w-3 h-3" /> Добавить
          </button>
        </div>
        {slots.styleHints && slots.styleHints.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {slots.styleHints.map((style, index) => (
              <span
                key={index}
                className="px-3 py-1 bg-wink-black rounded-lg border border-wink-gray text-sm flex items-center gap-2"
              >
                {style}
                <button
                  onClick={() => removeStyleHint(index)}
                  className="text-red-400 hover:text-red-300"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 italic">Стиль не задан</p>
        )}
      </div>

      {/* Негативы */}
      <div className="space-y-3">
        <h4 className="font-bold flex items-center gap-2">
          <Ban className="w-4 h-4 text-wink-orange" />
          Негативы
        </h4>
        <div className="space-y-3">
          <div className="p-3 bg-wink-black rounded-lg border border-wink-gray">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-gray-400">Глобальные</span>
              <button
                onClick={() => addNegative('global')}
                className="px-2 py-0.5 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors"
              >
                <Plus className="w-3 h-3 inline" />
              </button>
            </div>
            {slots.negatives?.global && slots.negatives.global.length > 0 ? (
              <div className="flex flex-wrap gap-1">
                {slots.negatives.global.map((neg, idx) => (
                  <span
                    key={idx}
                    className="px-2 py-0.5 bg-wink-dark rounded text-xs flex items-center gap-1"
                  >
                    {neg}
                    <button
                      onClick={() => removeNegative('global', idx)}
                      className="text-red-400 hover:text-red-300"
                    >
                      <X className="w-2 h-2" />
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">Глобальные негативы не заданы</p>
            )}
          </div>
          <div className="p-3 bg-wink-black rounded-lg border border-wink-gray">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-gray-400">Сценовые</span>
              <button
                onClick={() => addNegative('sceneSpecific')}
                className="px-2 py-0.5 text-xs border border-wink-gray rounded hover:border-wink-orange transition-colors"
              >
                <Plus className="w-3 h-3 inline" />
              </button>
            </div>
            {slots.negatives?.sceneSpecific && slots.negatives.sceneSpecific.length > 0 ? (
              <div className="flex flex-wrap gap-1">
                {slots.negatives.sceneSpecific.map((neg, idx) => (
                  <span
                    key={idx}
                    className="px-2 py-0.5 bg-wink-dark rounded text-xs flex items-center gap-1"
                  >
                    {neg}
                    <button
                      onClick={() => removeNegative('sceneSpecific', idx)}
                      className="text-red-400 hover:text-red-300"
                    >
                      <X className="w-2 h-2" />
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">Сценовые негативы не заданы</p>
            )}
          </div>
        </div>
      </div>

      {/* Освещение (legacy) */}
      <div className="space-y-2">
        <h4 className="font-bold text-sm">Освещение</h4>
        <textarea
          placeholder="Тип света и атмосфера"
          value={slots.lighting || ''}
          onChange={(e) => setSlots(prev => ({ ...prev, lighting: e.target.value }))}
          className="input-wink w-full text-xs h-16 resize-none"
        />
      </div>
    </div>
  );
};

export default PromptSlotsEditor;

