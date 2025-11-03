import { useState, useRef } from 'react';
import { Upload, FileText, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';
import { uploadScript } from '../api/apiClient';

const UploadScene = ({ onUploadSuccess }) => {
  const [file, setFile] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadStatus, setUploadStatus] = useState(null); // 'success', 'error', null
  const [errorMessage, setErrorMessage] = useState('');
  const fileInputRef = useRef(null);

  // Обработка выбора файла
  const handleFileSelect = (selectedFile) => {
    if (!selectedFile) return;

    // Проверка типа файла
    const validTypes = [
      'application/pdf',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/msword',
    ];

    if (!validTypes.includes(selectedFile.type)) {
      setErrorMessage('Пожалуйста, загрузите файл в формате PDF или DOCX');
      setUploadStatus('error');
      return;
    }

    // Проверка размера файла (макс 50MB)
    if (selectedFile.size > 50 * 1024 * 1024) {
      setErrorMessage('Размер файла не должен превышать 50MB');
      setUploadStatus('error');
      return;
    }

    setFile(selectedFile);
    setUploadStatus(null);
    setErrorMessage('');
  };

  // Drag and Drop обработчики
  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    const droppedFile = e.dataTransfer.files[0];
    handleFileSelect(droppedFile);
  };

  // Обработка загрузки файла на сервер
  const handleUpload = async () => {
    if (!file) return;

    setIsUploading(true);
    setUploadStatus(null);
    setErrorMessage('');

    try {
      const response = await uploadScript(file);
      setUploadStatus('success');
      
      // Передаем данные родительскому компоненту через 2 секунды
      setTimeout(() => {
        onUploadSuccess(response);
      }, 1500);
    } catch (error) {
      console.error('Upload error:', error);
      setUploadStatus('error');
      setErrorMessage(
        error.response?.data?.message || 
        'Произошла ошибка при загрузке файла. Попробуйте еще раз.'
      );
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 animate-fadeInUp">
      <div className="max-w-2xl w-full">
        {/* Логотип */}
        <div className="text-center mb-8">
          <img 
            src="/images/wink-logo.webp" 
            alt="Wink Logo" 
            className="h-16 mx-auto mb-6 filter brightness-0 invert"
          />
          <h1 className="text-4xl md:text-5xl font-cofo-black mb-4 text-gradient-wink">
            Wink PreViz
          </h1>
          <p className="text-gray-400 text-lg">
            Превратите ваш сценарий в визуальный storyboard
          </p>
        </div>

        {/* Зона загрузки */}
        <div
          className={`
            relative border-2 border-dashed rounded-xl p-12 text-center transition-all duration-300
            ${isDragging 
              ? 'border-wink-orange bg-wink-orange/10 scale-105' 
              : 'border-wink-gray hover:border-wink-orange/50'
            }
            ${file ? 'bg-wink-dark/50' : ''}
          `}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          {/* Иконка */}
          <div className="mb-6">
            {uploadStatus === 'success' ? (
              <CheckCircle2 className="w-20 h-20 mx-auto text-green-500" />
            ) : uploadStatus === 'error' ? (
              <AlertCircle className="w-20 h-20 mx-auto text-red-500" />
            ) : file ? (
              <FileText className="w-20 h-20 mx-auto text-wink-orange" />
            ) : (
              <Upload className="w-20 h-20 mx-auto text-wink-gray" />
            )}
          </div>

          {/* Текст */}
          <div className="mb-6">
            {uploadStatus === 'success' ? (
              <>
                <h3 className="text-xl font-bold text-green-500 mb-2">
                  Файл успешно загружен!
                </h3>
                <p className="text-gray-400">Обработка сценария...</p>
              </>
            ) : uploadStatus === 'error' ? (
              <>
                <h3 className="text-xl font-bold text-red-500 mb-2">
                  Ошибка загрузки
                </h3>
                <p className="text-gray-400">{errorMessage}</p>
              </>
            ) : file ? (
              <>
                <h3 className="text-xl font-bold mb-2">{file.name}</h3>
                <p className="text-gray-400">
                  {(file.size / 1024 / 1024).toFixed(2)} MB
                </p>
              </>
            ) : (
              <>
                <h3 className="text-xl font-bold mb-2">
                  Перетащите сценарий сюда
                </h3>
                <p className="text-gray-400">
                  или нажмите кнопку ниже для выбора файла
                </p>
                <p className="text-sm text-gray-500 mt-2">
                  Поддерживаются форматы: PDF, DOCX (макс. 50MB)
                </p>
              </>
            )}
          </div>

          {/* Кнопки */}
          <div className="flex gap-4 justify-center">
            {!file && !isUploading && uploadStatus !== 'success' && (
              <button
                onClick={() => fileInputRef.current?.click()}
                className="btn-wink"
              >
                Выбрать файл
              </button>
            )}

            {file && !isUploading && uploadStatus !== 'success' && (
              <>
                <button
                  onClick={handleUpload}
                  className="btn-wink"
                >
                  Загрузить сценарий
                </button>
                <button
                  onClick={() => {
                    setFile(null);
                    setUploadStatus(null);
                    setErrorMessage('');
                  }}
                  className="px-6 py-3 border border-wink-gray rounded-lg hover:border-wink-orange transition-colors"
                >
                  Отмена
                </button>
              </>
            )}

            {isUploading && (
              <div className="flex items-center gap-3 text-wink-orange">
                <Loader2 className="w-6 h-6 animate-spin" />
                <span>Загрузка...</span>
              </div>
            )}
          </div>

          {/* Скрытый input */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.docx,.doc"
            onChange={(e) => handleFileSelect(e.target.files[0])}
            className="hidden"
          />
        </div>

        {/* Дополнительная информация */}
        <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-4 text-center">
          <div className="card-wink">
            <div className="text-2xl mb-2">📄</div>
            <h4 className="font-bold mb-1">Загрузка</h4>
            <p className="text-sm text-gray-400">PDF или DOCX файл</p>
          </div>
          <div className="card-wink">
            <div className="text-2xl mb-2">🎬</div>
            <h4 className="font-bold mb-1">Анализ</h4>
            <p className="text-sm text-gray-400">Разбор на сцены</p>
          </div>
          <div className="card-wink">
            <div className="text-2xl mb-2">🎨</div>
            <h4 className="font-bold mb-1">Генерация</h4>
            <p className="text-sm text-gray-400">Создание визуализации</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UploadScene;

