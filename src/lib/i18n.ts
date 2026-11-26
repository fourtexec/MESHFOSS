import type { UiLanguageCode } from './meshCore';

export interface Translations {
  schedule: string;
  marks: string;
  homeworks: string;
  ranking: string;
  attendance: string;
  moskvenok: string;
  food: string;
  settings: string;
  more: string;
  accounts: string;
  signInMesh: string;
  demoMode: string;
  liveMesh: string;
  today: string;
  lessonInProgress: string;
  nextLesson: string;
  lessonsFinished: string;
  minutesLeft: string;
  room: string;
  homework: string;
  subjectAverage: string;
  overallGpa: string;
  targetCalculator: string;
  needMarksToReach: string;
  alreadyReached: string;
  unreachable: string;
  periodMarks: string;
  finalMarks: string;
  all: string;
  pending: string;
  done: string;
  cdzOnly: string;
  openCdzSolver: string;
  cdzTimerWarning: string;
  submitCdz: string;
  saveProgress: string;
  classRank: string;
  overallClass: string;
  bySubject: string;
  turnstileLog: string;
  absences: string;
  entered: string;
  exited: string;
  inSchool: string;
  mealBalance: string;
  dailyLimit: string;
  cafeteriaMenu: string;
  preorder: string;
  preordered: string;
  moskvenokCard: string;
  cardNumber: string;
  scanAtTurnstile: string;
  designStyle: string;
  liquidGlass: string;
  frostedGlass: string;
  classicGlass: string;
  plainMaterial: string;
  themePalette: string;
  language: string;
  liquidOptics: string;
  refractionDepth: string;
  chromaticDispersion: string;
  glassBlur: string;
  specularGlow: string;
  dockCustomization: string;
  desktopBuilds: string;
  searchPlaceholder: string;
  emptyHomework: string;
  close: string;
  apply: string;
}

const RU: Translations = {
  schedule: 'Расписание',
  marks: 'Оценки',
  homeworks: 'Задания',
  ranking: 'Рейтинг',
  attendance: 'Посещаемость',
  moskvenok: 'Москвёнок',
  food: 'Питание',
  settings: 'Настройки',
  more: 'Ещё',
  accounts: 'Аккаунты',
  signInMesh: 'Войти в МЭШ',
  demoMode: 'Демо-профиль МЭШ',
  liveMesh: 'Подключено к school.mos.ru',
  today: 'Сегодня',
  lessonInProgress: 'Идёт урок',
  nextLesson: 'Следующий урок',
  lessonsFinished: 'Уроки на сегодня завершены',
  minutesLeft: 'мин',
  room: 'каб.',
  homework: 'ДЗ',
  subjectAverage: 'Средний балл',
  overallGpa: 'Общий средний',
  targetCalculator: 'Калькулятор цели',
  needMarksToReach: 'Нужно пятёрок до',
  alreadyReached: 'Цель уже достигнута',
  unreachable: 'Недостижимо только пятёрками',
  periodMarks: 'Текущие оценки',
  finalMarks: 'Итоговые за год',
  all: 'Все',
  pending: 'К выполнению',
  done: 'Выполнено',
  cdzOnly: 'ЦДЗ',
  openCdzSolver: 'Решить ЦДЗ',
  cdzTimerWarning: 'Внимание: в материалах «Облака Знаний» может действовать таймер попытки.',
  submitCdz: 'Завершить и проверить',
  saveProgress: 'Сохранить ответы',
  classRank: 'Место в классе',
  overallClass: 'Общий рейтинг',
  bySubject: 'По предмету',
  turnstileLog: 'Проходы через турникет',
  absences: 'Пропуски по урокам',
  entered: 'Вход',
  exited: 'Выход',
  inSchool: 'В школе',
  mealBalance: 'Баланс питания',
  dailyLimit: 'Дневной лимит',
  cafeteriaMenu: 'Меню буфета и столовой',
  preorder: 'Предзаказ',
  preordered: 'В предзаказе',
  moskvenokCard: 'Карта «Москвёнок»',
  cardNumber: 'Номер карты',
  scanAtTurnstile: 'Покажите QR-код на турникете или в буфете',
  designStyle: 'Стиль интерфейса',
  liquidGlass: 'Liquid Glass (iOS 26)',
  frostedGlass: 'Матовое стекло',
  classicGlass: 'Стекло',
  plainMaterial: 'Material 3',
  themePalette: 'Цветовая тема',
  language: 'Язык интерфейса',
  liquidOptics: 'Оптика Liquid Glass',
  refractionDepth: 'Сила преломления',
  chromaticDispersion: 'Хроматическая дисперсия',
  glassBlur: 'Размытие подложки',
  specularGlow: 'Яркость бликов кромки',
  dockCustomization: 'Состав панели навигации',
  desktopBuilds: 'Сборка Desktop (Windows / Linux / macOS)',
  searchPlaceholder: 'Поиск по предмету или тексту…',
  emptyHomework: 'Нет заданий по выбранному фильтру.',
  close: 'Закрыть',
  apply: 'Применить',
};

const EN: Translations = {
  schedule: 'Schedule',
  marks: 'Grades',
  homeworks: 'Homework',
  ranking: 'Ranking',
  attendance: 'Attendance',
  moskvenok: 'Moskvenok',
  food: 'Meals',
  settings: 'Settings',
  more: 'More',
  accounts: 'Accounts',
  signInMesh: 'Sign in to MESH',
  demoMode: 'MESH Demo Profile',
  liveMesh: 'Connected to school.mos.ru',
  today: 'Today',
  lessonInProgress: 'Lesson in progress',
  nextLesson: 'Up next',
  lessonsFinished: 'All lessons finished for today',
  minutesLeft: 'min',
  room: 'Rm.',
  homework: 'HW',
  subjectAverage: 'Subject GPA',
  overallGpa: 'Overall GPA',
  targetCalculator: 'Target calculator',
  needMarksToReach: '5s needed for',
  alreadyReached: 'Target already reached',
  unreachable: 'Unreachable with 5s alone',
  periodMarks: 'Term grades',
  finalMarks: 'Final year grades',
  all: 'All',
  pending: 'Pending',
  done: 'Done',
  cdzOnly: 'CDZ',
  openCdzSolver: 'Solve CDZ',
  cdzTimerWarning: 'Note: Knowledge Cloud tests may enforce a strict attempt timer.',
  submitCdz: 'Submit & grade',
  saveProgress: 'Save answers',
  classRank: 'Class rank',
  overallClass: 'Overall class',
  bySubject: 'By subject',
  turnstileLog: 'School gate entries & exits',
  absences: 'Lesson absences',
  entered: 'Entered',
  exited: 'Exited',
  inSchool: 'In school',
  mealBalance: 'Meal balance',
  dailyLimit: 'Daily limit',
  cafeteriaMenu: 'Cafeteria menu',
  preorder: 'Pre-order',
  preordered: 'Pre-ordered',
  moskvenokCard: 'Moskvenok Pass',
  cardNumber: 'Card number',
  scanAtTurnstile: 'Present QR code at school turnstile or cafeteria',
  designStyle: 'Interface style',
  liquidGlass: 'Liquid Glass (iOS 26)',
  frostedGlass: 'Frosted Glass',
  classicGlass: 'Classic Glass',
  plainMaterial: 'Material 3',
  themePalette: 'Color palette',
  language: 'Language',
  liquidOptics: 'Liquid Glass optics',
  refractionDepth: 'Refraction strength',
  chromaticDispersion: 'Chromatic dispersion',
  glassBlur: 'Backdrop blur',
  specularGlow: 'Specular rim intensity',
  dockCustomization: 'Dock tab customization',
  desktopBuilds: 'Desktop builds (Windows / Linux / macOS)',
  searchPlaceholder: 'Search by subject or keyword…',
  emptyHomework: 'No homework entries match this filter.',
  close: 'Close',
  apply: 'Apply',
};

const ES: Translations = {
  ...EN,
  schedule: 'Horario',
  marks: 'Notas',
  homeworks: 'Tareas',
  ranking: 'Ranking',
  attendance: 'Asistencia',
  food: 'Comedor',
  settings: 'Ajustes',
  more: 'Más',
  accounts: 'Cuentas',
  signInMesh: 'Entrar en MESH',
  language: 'Idioma',
  themePalette: 'Tema de color',
  designStyle: 'Estilo de interfaz',
};

const FR: Translations = {
  ...EN,
  schedule: 'Emploi du temps',
  marks: 'Notes',
  homeworks: 'Devoirs',
  ranking: 'Classement',
  attendance: 'Présence',
  food: 'Cantine',
  settings: 'Paramètres',
  more: 'Plus',
  accounts: 'Comptes',
  signInMesh: 'Connexion MESH',
  language: 'Langue',
  themePalette: 'Palette',
  designStyle: 'Style visuel',
};

const HU: Translations = {
  ...EN,
  schedule: 'Órarend',
  marks: 'Jegyek',
  homeworks: 'Házi feladat',
  ranking: 'Rangsor',
  attendance: 'Jelenlét',
  food: 'Étkezés',
  settings: 'Beállítások',
  more: 'Több',
  accounts: 'Fiókok',
  signInMesh: 'Bejelentkezés',
  language: 'Nyelv',
  themePalette: 'Színtéma',
  designStyle: 'Felület stílusa',
};

const DICTS: Record<UiLanguageCode, Translations> = {
  ru: RU,
  en: EN,
  es: ES,
  fr: FR,
  hu: HU,
};

export function t(lang: UiLanguageCode): Translations {
  return DICTS[lang] ?? RU;
}
