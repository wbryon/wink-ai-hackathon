import { useState } from 'react';
import WorkflowManager from '../components/WorkflowManager';
import { Settings as SettingsIcon, ChevronLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

/**
 * Страница настроек системы
 * Включает управление workflow файлами
 */
const SettingsPage = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-wink-black text-white">
      {/* Заголовок */}
      <header className="border-b border-wink-gray bg-wink-dark/80 sticky top-0 z-40 backdrop-blur">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/')}
              className="p-2 hover:bg-wink-gray rounded-lg transition-colors"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            <SettingsIcon className="w-6 h-6 text-wink-orange" />
            <div>
              <div className="text-sm uppercase tracking-widest text-gray-400">
                Настройки
              </div>
              <div className="text-lg font-cofo-black text-gradient-wink">
                Управление системой
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Контент */}
      <div className="py-6">
        <WorkflowManager />
      </div>
    </div>
  );
};

export default SettingsPage;

