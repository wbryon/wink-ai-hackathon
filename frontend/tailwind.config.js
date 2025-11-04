/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'wink-black': '#000000',
        'wink-dark': '#171717',
        'wink-gray': '#464645',
        'wink-orange': '#ff5b21',
        'wink-orange-light': '#FF9532',
        'wink-orange-mid': '#FF8A1C',
        'wink-orange-dark': '#FF5A24',
      },
      fontFamily: {
        'cofo': ['CoFoSans', 'Arial', 'sans-serif'],
        'cofo-black': ['CoFoKak-Black', 'Arial', 'sans-serif'],
      },
      backgroundImage: {
        'wink-gradient': 'linear-gradient(27.5deg, #FF9532 0%, #FF8A1C 31%, #FF5B21 46%, #FF5A24 67%)',
      },
    },
  },
  plugins: [],
}

