/**
 * Mock данные для тестирования без бэкенда
 * Используйте эти данные для разработки и демонстрации
 */

export const mockScenes = [
  {
    id: 'scene-1',
    sceneNumber: 1,
    title: 'Встреча в кафе',
    location: 'Кафе "Старбакс", день',
    characters: ['Анна', 'Борис'],
    props: ['Кофе', 'Ноутбук', 'Телефон'],
    description: 'Анна входит в оживленное кафе и видит Бориса, сидящего за столиком у окна. Он работает за ноутбуком, не замечая её приближения.',
    prompt: 'Современное кафе Starbucks, яркий дневной свет из больших окон, молодая женщина в пальто входит, мужчина за столиком с ноутбуком, чашка кофе, теплая атмосфера, стиль кинематографической визуализации',
    currentFrame: {
      id: 'frame-1-1',
      imageUrl: 'https://picsum.photos/seed/scene1/1200/675',
      detailLevel: 'medium',
      createdAt: '2025-11-02T10:00:00Z',
    },
    generatedFrames: [
      {
        id: 'frame-1-1',
        imageUrl: 'https://picsum.photos/seed/scene1/1200/675',
        detailLevel: 'medium',
        createdAt: '2025-11-02T10:00:00Z',
      },
      {
        id: 'frame-1-2',
        imageUrl: 'https://picsum.photos/seed/scene1v2/1200/675',
        detailLevel: 'sketch',
        createdAt: '2025-11-02T09:45:00Z',
      },
    ],
  },
  {
    id: 'scene-2',
    sceneNumber: 2,
    title: 'Напряженный разговор',
    location: 'Кафе "Старбакс", день (продолжение)',
    characters: ['Анна', 'Борис'],
    props: ['Документы', 'Кофе'],
    description: 'Анна садится напротив Бориса и достает папку с документами. Её лицо серьёзно. Борис закрывает ноутбук, понимая важность момента.',
    prompt: 'Крупный план двух людей за столиком кафе, серьёзное выражение лиц, папка с документами на столе, напряженная атмосфера, драматический свет',
    currentFrame: null,
    generatedFrames: [],
  },
  {
    id: 'scene-3',
    sceneNumber: 3,
    title: 'Прогулка по парку',
    location: 'Городской парк, вечер',
    characters: ['Анна', 'Борис'],
    props: ['Зонт', 'Сумка'],
    description: 'После встречи Анна и Борис идут через осенний парк. Моросит дождь, они делят один зонт. Разговор стал более доверительным.',
    prompt: 'Осенний городской парк в сумерках, лёгкий дождь, пара под одним зонтом идёт по аллее с жёлтыми листьями, романтическая атмосфера, киношная картинка',
    currentFrame: {
      id: 'frame-3-1',
      imageUrl: 'https://picsum.photos/seed/scene3/1200/675',
      detailLevel: 'final',
      createdAt: '2025-11-02T10:30:00Z',
    },
    generatedFrames: [
      {
        id: 'frame-3-1',
        imageUrl: 'https://picsum.photos/seed/scene3/1200/675',
        detailLevel: 'final',
        createdAt: '2025-11-02T10:30:00Z',
      },
    ],
  },
  {
    id: 'scene-4',
    sceneNumber: 4,
    title: 'Офисное противостояние',
    location: 'Офис компании, утро',
    characters: ['Борис', 'Виктор', 'Екатерина'],
    props: ['Компьютеры', 'Документы', 'Проектор'],
    description: 'Борис входит в конференц-зал, где его уже ждут Виктор и Екатерина. На экране проектора отображены компрометирующие данные.',
    prompt: 'Современный офис, конференц-зал, трое людей в деловых костюмах, презентация на большом экране, холодный офисный свет, напряженная деловая атмосфера',
    currentFrame: null,
    generatedFrames: [],
  },
  {
    id: 'scene-5',
    sceneNumber: 5,
    title: 'Финальная развязка',
    location: 'Набережная, ночь',
    characters: ['Анна', 'Борис'],
    props: ['Телефон', 'Пальто'],
    description: 'Анна и Борис стоят на пустой набережной. Огни города отражаются в воде. Анна передает Борису телефон с важной информацией. Это конец их совместного пути.',
    prompt: 'Ночная городская набережная, отражения огней в воде, силуэты двух людей на фоне города, драматическое освещение, кинематографичный финал, эмоциональная сцена',
    currentFrame: {
      id: 'frame-5-1',
      imageUrl: 'https://picsum.photos/seed/scene5/1200/675',
      detailLevel: 'medium',
      createdAt: '2025-11-02T11:00:00Z',
    },
    generatedFrames: [
      {
        id: 'frame-5-1',
        imageUrl: 'https://picsum.photos/seed/scene5/1200/675',
        detailLevel: 'medium',
        createdAt: '2025-11-02T11:00:00Z',
      },
      {
        id: 'frame-5-2',
        imageUrl: 'https://picsum.photos/seed/scene5v2/1200/675',
        detailLevel: 'sketch',
        createdAt: '2025-11-02T10:45:00Z',
      },
    ],
  },
];

export const mockScriptData = {
  scriptId: 'mock-script-123',
  filename: 'sample_scenario.pdf',
  status: 'completed',
  scenes: mockScenes,
};

/**
 * Симуляция задержки сети
 */
export const delay = (ms = 1000) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Mock API функции для тестирования без бэкенда
 */
export const mockAPI = {
  uploadScript: async (file) => {
    await delay(2000);
    return mockScriptData;
  },

  getScenes: async (scriptId) => {
    await delay(500);
    return mockScenes;
  },

  updateScene: async (sceneId, sceneData) => {
    await delay(300);
    return { id: sceneId, ...sceneData };
  },

  deleteScene: async (sceneId) => {
    await delay(300);
    return { success: true };
  },

  addScene: async (scriptId, sceneData) => {
    await delay(500);
    return {
      id: `scene-${Date.now()}`,
      sceneNumber: mockScenes.length + 1,
      ...sceneData,
      prompt: null,
      currentFrame: null,
      generatedFrames: [],
    };
  },

  generateFrame: async (sceneId, detailLevel) => {
    await delay(3000); // Симуляция долгой генерации
    const randomSeed = Math.random().toString(36).substring(7);
    return {
      id: `frame-${Date.now()}`,
      imageUrl: `https://picsum.photos/seed/${randomSeed}/1200/675`,
      detailLevel,
      prompt: 'Автоматически сгенерированный промпт...',
      createdAt: new Date().toISOString(),
    };
  },

  regenerateFrame: async (frameId, prompt, detailLevel) => {
    await delay(3000);
    const randomSeed = Math.random().toString(36).substring(7);
    return {
      id: `frame-${Date.now()}`,
      imageUrl: `https://picsum.photos/seed/${randomSeed}/1200/675`,
      detailLevel,
      prompt,
      createdAt: new Date().toISOString(),
    };
  },

  getFrameHistory: async (sceneId) => {
    await delay(300);
    const scene = mockScenes.find(s => s.id === sceneId);
    return scene?.generatedFrames || [];
  },

  exportStoryboard: async (scriptId) => {
    await delay(2000);
    // В реальном приложении это должен быть Blob PDF
    // Для демо просто возвращаем пустой blob
    return new Blob(['Mock PDF content'], { type: 'application/pdf' });
  },
};

