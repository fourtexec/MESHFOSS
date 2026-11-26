import type { WeightedMark } from '../lib/meshCore';

export interface MeshAccount {
  id: string;
  name: string;
  initials: string;
  role: 'student' | 'parent';
  school: string;
  className: string;
  profileId: string;
  personId: string;
  isLive: boolean;
  tokenPreview?: string;
}

export interface ScheduleLesson {
  id: string;
  dayIndex: number; // 0 = Mon .. 4 = Fri
  number: number;
  subject: string;
  teacher: string;
  room: string;
  startTime: string;
  endTime: string;
  startMinutes: number;
  endMinutes: number;
  topic: string;
  homeworkPreview?: string;
  homeworkId?: string;
  mark?: { value: number; weight: number; comment: string };
}

export interface SubjectMarkItem extends WeightedMark {
  id: string;
  date: string;
  controlForm: string;
  comment: string;
  lessonTopic: string;
}

export interface SubjectGrades {
  subjectId: string;
  subject: string;
  teacher: string;
  marks: SubjectMarkItem[];
  term1: number;
  term2: number;
  yearForecast: number;
}

export interface CdzQuestion {
  id: string;
  prompt: string;
  type: 'choice' | 'input';
  options?: string[];
  correctAnswer: string;
  hint: string;
}

export interface CdzMaterial {
  id: string;
  title: string;
  publisher: string;
  hasTimer: boolean;
  durationMinutes?: number;
  status: 'not_started' | 'in_progress' | 'completed';
  scorePercent?: number;
  questions: CdzQuestion[];
}

export interface HomeworkItem {
  id: string;
  subject: string;
  dueDate: string;
  dueLabel: string;
  description: string;
  done: boolean;
  cdz?: CdzMaterial;
}

export interface ClassRankEntry {
  rank: number;
  name: string;
  average: number;
  delta: number;
  isCurrentUser?: boolean;
  subjectAverages: Record<string, number>;
}

export interface AttendancePass {
  id: string;
  date: string;
  weekday: string;
  entryTime: string;
  exitTime: string;
  durationLabel: string;
  gate: string;
}

export interface AttendanceAbsence {
  id: string;
  date: string;
  subject: string;
  reason: 'valid' | 'unexcused' | 'late';
  note: string;
}

export interface CafeteriaDish {
  id: string;
  name: string;
  category: 'hot' | 'salad' | 'drink' | 'bakery';
  calories: number;
  weightGrams: number;
  priceRub: number;
  preordered: boolean;
}

export interface MealTransaction {
  id: string;
  date: string;
  description: string;
  amountRub: number;
}

export const SEED_ACCOUNTS: MeshAccount[] = [
  {
    id: 'acc-student-1',
    name: 'Артём Соколов',
    initials: 'АС',
    role: 'student',
    school: 'ГБОУ Школа № 1535, Москва',
    className: '10 «Б» · Физмат',
    profileId: '39481204',
    personId: '8f12c940-2b4a-4e11-a09d-74c8e211b901',
    isLive: false,
  },
  {
    id: 'acc-parent-1',
    name: 'Елена Соколова',
    initials: 'ЕС',
    role: 'parent',
    school: 'ГБОУ Школа № 1535, Москва',
    className: 'Родитель · 2 ребёнка',
    profileId: '10928374',
    personId: '2c44d819-91e7-4c19-992b-18a4e320f012',
    isLive: false,
  },
];

export const WEEKDAYS = [
  { index: 0, shortRu: 'Пн', shortEn: 'Mon', dateLabel: '23 ноя' },
  { index: 1, shortRu: 'Вт', shortEn: 'Tue', dateLabel: '24 ноя' },
  { index: 2, shortRu: 'Ср', shortEn: 'Wed', dateLabel: '25 ноя' },
  { index: 3, shortRu: 'Чт', shortEn: 'Thu', dateLabel: '26 ноя' },
  { index: 4, shortRu: 'Пт', shortEn: 'Fri', dateLabel: '27 ноя' },
];

export const SEED_SCHEDULE: ScheduleLesson[] = [
  // Wednesday (today, index 2)
  {
    id: 'les-w1',
    dayIndex: 2,
    number: 1,
    subject: 'Алгебра и начала анализа',
    teacher: 'Морозова И. В.',
    room: '304',
    startTime: '08:30',
    endTime: '09:15',
    startMinutes: 510,
    endMinutes: 555,
    topic: 'Тригонометрические уравнения и отбор корней на окружности',
    homeworkPreview: '№ 14.12 (а, б), № 14.18 + ЦДЗ «Тригонометрия»',
    homeworkId: 'hw-1',
    mark: { value: 5, weight: 2, comment: 'Самостоятельная работа у доски' },
  },
  {
    id: 'les-w2',
    dayIndex: 2,
    number: 2,
    subject: 'Физика',
    teacher: 'Волков А. С.',
    room: '215',
    startTime: '09:25',
    endTime: '10:10',
    startMinutes: 565,
    endMinutes: 610,
    topic: 'Закон сохранения импульса в замкнутой системе',
    homeworkPreview: '§ 26, задачи 1–4 в конце параграфа',
    homeworkId: 'hw-2',
  },
  {
    id: 'les-w3',
    dayIndex: 2,
    number: 3,
    subject: 'Информатика',
    teacher: 'Кузнецов Д. Е.',
    room: '408',
    startTime: '10:30',
    endTime: '11:15',
    startMinutes: 630,
    endMinutes: 675,
    topic: 'Рекурсивные алгоритмы и динамическое программирование на Python',
    homeworkPreview: 'ЦДЗ «Динамическое программирование: базовые задачи»',
    homeworkId: 'hw-3',
    mark: { value: 5, weight: 3, comment: 'Практическая работа: алгоритм рюкзака' },
  },
  {
    id: 'les-w4',
    dayIndex: 2,
    number: 4,
    subject: 'Английский язык',
    teacher: 'Смирнова О. П.',
    room: '312',
    startTime: '11:35',
    endTime: '12:20',
    startMinutes: 695,
    endMinutes: 740,
    topic: 'Conditionals & Mixed Hypotheticals (Unit 5B)',
    homeworkPreview: 'Workbook p. 44 ex. 1–3, эссе 140 слов',
    homeworkId: 'hw-4',
    mark: { value: 4, weight: 1, comment: 'Устный ответ на уроке' },
  },
  {
    id: 'les-w5',
    dayIndex: 2,
    number: 5,
    subject: 'История России',
    teacher: 'Белова Н. Г.',
    room: '201',
    startTime: '12:40',
    endTime: '13:25',
    startMinutes: 760,
    endMinutes: 805,
    topic: 'Реформы середины XIX века и отмена крепостного права',
    homeworkPreview: '§ 18, таблица ключевых реформ Александра II',
    homeworkId: 'hw-5',
  },
  {
    id: 'les-w6',
    dayIndex: 2,
    number: 6,
    subject: 'Геометрия',
    teacher: 'Морозова И. В.',
    room: '304',
    startTime: '13:35',
    endTime: '14:20',
    startMinutes: 815,
    endMinutes: 860,
    topic: 'Перпендикулярность прямой и плоскости, теорема о трёх перпендикулярах',
    homeworkPreview: 'Доказательство теоремы + № 145, 148',
    homeworkId: 'hw-6',
  },
  // Monday (index 0)
  {
    id: 'les-m1',
    dayIndex: 0,
    number: 1,
    subject: 'Русский язык',
    teacher: 'Орлова Т. А.',
    room: '118',
    startTime: '08:30',
    endTime: '09:15',
    startMinutes: 510,
    endMinutes: 555,
    topic: 'Пунктуация в сложноподчинённых предложениях с несколькими придаточными',
    mark: { value: 5, weight: 2, comment: 'Словарный диктант' },
  },
  {
    id: 'les-m2',
    dayIndex: 0,
    number: 2,
    subject: 'Алгебра и начала анализа',
    teacher: 'Морозова И. В.',
    room: '304',
    startTime: '09:25',
    endTime: '10:10',
    startMinutes: 565,
    endMinutes: 610,
    topic: 'Формулы двойного и половинного угла',
  },
  {
    id: 'les-m3',
    dayIndex: 0,
    number: 3,
    subject: 'Физика',
    teacher: 'Волков А. С.',
    room: '215',
    startTime: '10:30',
    endTime: '11:15',
    startMinutes: 630,
    endMinutes: 675,
    topic: 'Лабораторная работа № 4: упругий удар шаров',
    mark: { value: 5, weight: 2, comment: 'Отчёт по лабораторной работе' },
  },
  // Tuesday (index 1)
  {
    id: 'les-t1',
    dayIndex: 1,
    number: 1,
    subject: 'Информатика',
    teacher: 'Кузнецов Д. Е.',
    room: '408',
    startTime: '08:30',
    endTime: '09:15',
    startMinutes: 510,
    endMinutes: 555,
    topic: 'Графы: поиск в ширину (BFS) и кратчайшие пути',
  },
  {
    id: 'les-t2',
    dayIndex: 1,
    number: 2,
    subject: 'Химия',
    teacher: 'Громова М. Л.',
    room: '320',
    startTime: '09:25',
    endTime: '10:10',
    startMinutes: 565,
    endMinutes: 610,
    topic: 'Алканы и циклоалканы: изомерия и номенклатура ИЮПАК',
    mark: { value: 4, weight: 2, comment: 'Контрольный тест по изомерии' },
  },
  {
    id: 'les-t3',
    dayIndex: 1,
    number: 3,
    subject: 'Литература',
    teacher: 'Орлова Т. А.',
    room: '118',
    startTime: '10:30',
    endTime: '11:15',
    startMinutes: 630,
    endMinutes: 675,
    topic: 'Л. Н. Толстой «Война и мир»: образ Андрея Болконского',
  },
  // Thursday (index 3)
  {
    id: 'les-th1',
    dayIndex: 3,
    number: 1,
    subject: 'Геометрия',
    teacher: 'Морозова И. В.',
    room: '304',
    startTime: '08:30',
    endTime: '09:15',
    startMinutes: 510,
    endMinutes: 555,
    topic: 'Двугранный угол и угол между плоскостями',
  },
  {
    id: 'les-th2',
    dayIndex: 3,
    number: 2,
    subject: 'Обществознание',
    teacher: 'Белова Н. Г.',
    room: '201',
    startTime: '09:25',
    endTime: '10:10',
    startMinutes: 565,
    endMinutes: 610,
    topic: 'Рыночный механизм, спрос и предложение',
  },
  // Friday (index 4)
  {
    id: 'les-f1',
    dayIndex: 4,
    number: 1,
    subject: 'Физика',
    teacher: 'Волков А. С.',
    room: '215',
    startTime: '08:30',
    endTime: '09:15',
    startMinutes: 510,
    endMinutes: 555,
    topic: 'Кинетическая и потенциальная энергия, закон сохранения энергии',
  },
  {
    id: 'les-f2',
    dayIndex: 4,
    number: 2,
    subject: 'Информатика',
    teacher: 'Кузнецов Д. Е.',
    room: '408',
    startTime: '09:25',
    endTime: '10:10',
    startMinutes: 565,
    endMinutes: 610,
    topic: 'Подготовка к олимпиаде: деревья отрезков',
  },
];

export const SEED_GRADES: SubjectGrades[] = [
  {
    subjectId: 'alg',
    subject: 'Алгебра и начала анализа',
    teacher: 'Морозова И. В.',
    term1: 5,
    term2: 5,
    yearForecast: 5,
    marks: [
      { id: 'm1', value: 5, weight: 2, date: '25 ноя', controlForm: 'Самостоятельная работа', comment: 'Безупречный отбор корней', lessonTopic: 'Тригонометрические уравнения' },
      { id: 'm2', value: 5, weight: 3, date: '19 ноя', controlForm: 'Контрольная работа', comment: 'Вариант 2, 100% верно', lessonTopic: 'Преобразование выражений' },
      { id: 'm3', value: 4, weight: 1, date: '12 ноя', controlForm: 'Работа на уроке', comment: 'Вычислительная неточность в п. 3', lessonTopic: 'Формулы приведения' },
      { id: 'm4', value: 5, weight: 2, date: '05 ноя', controlForm: 'ЦДЗ', comment: 'Автоматическая проверка LRS', lessonTopic: 'Радианная мера угла' },
    ],
  },
  {
    subjectId: 'inf',
    subject: 'Информатика',
    teacher: 'Кузнецов Д. Е.',
    term1: 5,
    term2: 5,
    yearForecast: 5,
    marks: [
      { id: 'm5', value: 5, weight: 3, date: '25 ноя', controlForm: 'Практическая работа', comment: 'Оптимальная сложность O(N*W)', lessonTopic: 'Динамическое программирование' },
      { id: 'm6', value: 5, weight: 2, date: '17 ноя', controlForm: 'Лабораторная работа', comment: 'Чистая реализация BFS', lessonTopic: 'Алгоритмы на графах' },
      { id: 'm7', value: 5, weight: 2, date: '08 ноя', controlForm: 'ЦДЗ', comment: '10 из 10 баллов', lessonTopic: 'Рекурсия и мемоизация' },
    ],
  },
  {
    subjectId: 'phy',
    subject: 'Физика',
    teacher: 'Волков А. С.',
    term1: 5,
    term2: 4,
    yearForecast: 5,
    marks: [
      { id: 'm8', value: 5, weight: 2, date: '23 ноя', controlForm: 'Лабораторная работа', comment: 'Точный расчёт погрешности', lessonTopic: 'Упругий удар шаров' },
      { id: 'm9', value: 4, weight: 3, date: '15 ноя', controlForm: 'Контрольная работа', comment: 'Не указано направление оси в задаче 4', lessonTopic: 'Законы Ньютона' },
      { id: 'm10', value: 5, weight: 1, date: '09 ноя', controlForm: 'Устный ответ', comment: 'Уверенный вывод формулы', lessonTopic: 'Сила трения' },
      { id: 'm11', value: 4, weight: 2, date: '02 ноя', controlForm: 'Самостоятельная работа', comment: 'Верный ход решения', lessonTopic: 'Кинематика движения по окружности' },
    ],
  },
  {
    subjectId: 'eng',
    subject: 'Английский язык',
    teacher: 'Смирнова О. П.',
    term1: 4,
    term2: 5,
    yearForecast: 5,
    marks: [
      { id: 'm12', value: 4, weight: 1, date: '25 ноя', controlForm: 'Устный ответ', comment: 'Good fluency, minor tense slip', lessonTopic: 'Mixed Conditionals' },
      { id: 'm13', value: 5, weight: 2, date: '18 ноя', controlForm: 'Эссе', comment: 'Strong structure & vocabulary', lessonTopic: 'Academic Writing' },
      { id: 'm14', value: 4, weight: 2, date: '10 ноя', controlForm: 'Аудирование', comment: '17/20 points', lessonTopic: 'Listening Comprehension' },
    ],
  },
  {
    subjectId: 'rus',
    subject: 'Русский язык',
    teacher: 'Орлова Т. А.',
    term1: 5,
    term2: 5,
    yearForecast: 5,
    marks: [
      { id: 'm15', value: 5, weight: 2, date: '23 ноя', controlForm: 'Диктант', comment: 'Без орфографических ошибок', lessonTopic: 'Сложноподчинённые предложения' },
      { id: 'm16', value: 5, weight: 3, date: '14 ноя', controlForm: 'Сочинение', comment: 'Глубокая аргументация', lessonTopic: 'Анализ художественного текста' },
      { id: 'm17', value: 4, weight: 1, date: '06 ноя', controlForm: 'Тест', comment: '1 ошибка в пунктуации', lessonTopic: 'Вводные конструкции' },
    ],
  },
  {
    subjectId: 'his',
    subject: 'История России',
    teacher: 'Белова Н. Г.',
    term1: 4,
    term2: 5,
    yearForecast: 5,
    marks: [
      { id: 'm18', value: 5, weight: 2, date: '20 ноя', controlForm: 'Доклад', comment: 'Отличная работа с источниками', lessonTopic: 'Великие реформы 1860-х' },
      { id: 'm19', value: 4, weight: 2, date: '11 ноя', controlForm: 'Проверочная работа', comment: 'Путаница в двух датах', lessonTopic: 'Крымская война' },
    ],
  },
  {
    subjectId: 'chem',
    subject: 'Химия',
    teacher: 'Громова М. Л.',
    term1: 4,
    term2: 4,
    yearForecast: 4,
    marks: [
      { id: 'm20', value: 4, weight: 2, date: '24 ноя', controlForm: 'Контрольный тест', comment: '84% правильных ответов', lessonTopic: 'Изомерия алканов' },
      { id: 'm21', value: 5, weight: 1, date: '13 ноя', controlForm: 'Домашняя работа', comment: 'Аккуратные структурные формулы', lessonTopic: 'Номенклатура ИЮПАК' },
      { id: 'm22', value: 4, weight: 2, date: '04 ноя', controlForm: 'Практическая работа', comment: 'Незначительная неточность в уравнении', lessonTopic: 'Качественные реакции' },
    ],
  },
];

export const SEED_HOMEWORKS: HomeworkItem[] = [
  {
    id: 'hw-1',
    subject: 'Алгебра и начала анализа',
    dueDate: '2026-11-26',
    dueLabel: 'Завтра, 26 ноя',
    description: 'Решить № 14.12 (а, б), № 14.18 из задачника Мордковича и пройти цифровой тест ЦДЗ на отбор корней тригонометрических уравнений.',
    done: false,
    cdz: {
      id: 'cdz-trig-101',
      title: 'Тригонометрические уравнения и окружность',
      publisher: 'МЭШ · Облако Знаний',
      hasTimer: true,
      durationMinutes: 15,
      status: 'not_started',
      questions: [
        {
          id: 'q1',
          prompt: 'Чему равно значение sin(π/6) + cos(π/3)?',
          type: 'choice',
          options: ['0.5', '1', '√3 / 2', '2'],
          correctAnswer: '1',
          hint: 'Оба слагаемых равны 1/2.',
        },
        {
          id: 'q2',
          prompt: 'Сколько корней имеет уравнение cos(x) = 0 на отрезке [0; 2π]?',
          type: 'choice',
          options: ['1', '2', '3', '4'],
          correctAnswer: '2',
          hint: 'Проверьте точки π/2 и 3π/2 на единичной окружности.',
        },
        {
          id: 'q3',
          prompt: 'Введите наименьший положительный корень уравнения tg(x) = 1 в градусах (только число):',
          type: 'input',
          correctAnswer: '45',
          hint: 'tg(45°) = 1.',
        },
      ],
    },
  },
  {
    id: 'hw-3',
    subject: 'Информатика',
    dueDate: '2026-11-27',
    dueLabel: 'Пт, 27 ноя',
    description: 'Выполнить нативное ЦДЗ по динамическому программированию: одномерные массивы переходов и оценка асимптотики.',
    done: false,
    cdz: {
      id: 'cdz-dp-204',
      title: 'Динамическое программирование: базовые задачи',
      publisher: 'Библиотека МЭШ',
      hasTimer: false,
      status: 'not_started',
      questions: [
        {
          id: 'dp1',
          prompt: 'Какова временная сложность вычисления N-го числа Фибоначчи с мемоизацией?',
          type: 'choice',
          options: ['O(1)', 'O(log N)', 'O(N)', 'O(2^N)'],
          correctAnswer: 'O(N)',
          hint: 'Каждое состояние от 0 до N вычисляется ровно один раз.',
        },
        {
          id: 'dp2',
          prompt: 'Сколько способов подняться на 4-ю ступеньку, если можно шагать на 1 или 2 ступеньки (старт с 0)? Введите число:',
          type: 'input',
          correctAnswer: '5',
          hint: 'dp[0]=1, dp[1]=1, dp[2]=2, dp[3]=3, dp[4]=5.',
        },
      ],
    },
  },
  {
    id: 'hw-2',
    subject: 'Физика',
    dueDate: '2026-11-27',
    dueLabel: 'Пт, 27 ноя',
    description: 'Прочитать § 26 «Закон сохранения импульса», письменно решить задачи 1–4 в конце параграфа с чертежами векторов.',
    done: false,
  },
  {
    id: 'hw-4',
    subject: 'Английский язык',
    dueDate: '2026-11-26',
    dueLabel: 'Завтра, 26 ноя',
    description: 'Workbook p. 44 ex. 1–3; написать эссе (140–160 слов) на тему «Can technology replace printed books?».',
    done: true,
  },
  {
    id: 'hw-5',
    subject: 'История России',
    dueDate: '2026-11-28',
    dueLabel: 'Сб, 28 ноя',
    description: '§ 18, составить сравнительную таблицу Земской, Судебной и Военной реформ Александра II.',
    done: false,
  },
  {
    id: 'hw-6',
    subject: 'Геометрия',
    dueDate: '2026-11-26',
    dueLabel: 'Завтра, 26 ноя',
    description: 'Выучить доказательство теоремы о трёх перпендикулярах, решить задачи № 145 и № 148.',
    done: true,
  },
];

export const SEED_RANKING: ClassRankEntry[] = [
  { rank: 1, name: 'Полина Васильева', average: 4.91, delta: 0, subjectAverages: { alg: 4.95, inf: 4.88, phy: 4.92, eng: 5.0 } },
  { rank: 2, name: 'Артём Соколов', average: 4.78, delta: +1, isCurrentUser: true, subjectAverages: { alg: 4.88, inf: 5.0, phy: 4.38, eng: 4.4 } },
  { rank: 3, name: 'Максим Лебедев', average: 4.74, delta: -1, subjectAverages: { alg: 4.72, inf: 4.9, phy: 4.75, eng: 4.6 } },
  { rank: 4, name: 'София Петрова', average: 4.66, delta: +2, subjectAverages: { alg: 4.6, inf: 4.5, phy: 4.55, eng: 4.95 } },
  { rank: 5, name: 'Даниил Козлов', average: 4.52, delta: 0, subjectAverages: { alg: 4.5, inf: 4.8, phy: 4.4, eng: 4.35 } },
  { rank: 6, name: 'Анна Новикова', average: 4.41, delta: -2, subjectAverages: { alg: 4.35, inf: 4.2, phy: 4.3, eng: 4.8 } },
  { rank: 7, name: 'Илья Морозов', average: 4.28, delta: +1, subjectAverages: { alg: 4.2, inf: 4.6, phy: 4.15, eng: 4.1 } },
];

export const SEED_PASSES: AttendancePass[] = [
  { id: 'p1', date: '25 ноя', weekday: 'Среда', entryTime: '08:14', exitTime: '—', durationLabel: 'В школе · 5 ч 12 мин', gate: 'Главный вход · Турникет 2' },
  { id: 'p2', date: '24 ноя', weekday: 'Вторник', entryTime: '08:19', exitTime: '14:38', durationLabel: '6 ч 19 мин', gate: 'Главный вход · Турникет 1' },
  { id: 'p3', date: '23 ноя', weekday: 'Понедельник', entryTime: '08:11', exitTime: '15:05', durationLabel: '6 ч 54 мин', gate: 'Главный вход · Турникет 2' },
  { id: 'p4', date: '20 ноя', weekday: 'Пятница', entryTime: '08:22', exitTime: '14:27', durationLabel: '6 ч 05 мин', gate: 'Западный вход · Турникет 3' },
];

export const SEED_ABSENCES: AttendanceAbsence[] = [
  { id: 'ab1', date: '18 ноя', subject: 'Физическая культура', reason: 'valid', note: 'Освобождение после олимпиады (справка)' },
  { id: 'ab2', date: '11 ноя', subject: 'Английский язык', reason: 'late', note: 'Опоздание на 6 минут' },
];

export const SEED_MENU: CafeteriaDish[] = [
  { id: 'd1', name: 'Куриное филе запечённое с гречкой', category: 'hot', calories: 420, weightGrams: 260, priceRub: 185, preordered: true },
  { id: 'd2', name: 'Крем-суп из тыквы с гренками', category: 'hot', calories: 215, weightGrams: 250, priceRub: 115, preordered: false },
  { id: 'd3', name: 'Салат из свежих овощей с оливковым маслом', category: 'salad', calories: 130, weightGrams: 120, priceRub: 85, preordered: true },
  { id: 'd4', name: 'Сырники творожные со сметаной', category: 'bakery', calories: 340, weightGrams: 150, priceRub: 125, preordered: false },
  { id: 'd5', name: 'Морс клюквенный охлаждённый', category: 'drink', calories: 95, weightGrams: 200, priceRub: 55, preordered: true },
  { id: 'd6', name: 'Булочка с корицей свежей выпечки', category: 'bakery', calories: 270, weightGrams: 90, priceRub: 65, preordered: false },
];

export const SEED_TRANSACTIONS: MealTransaction[] = [
  { id: 'tx1', date: '25 ноя, 10:18', description: 'Оплата в буфете · Обед стандарт', amountRub: -270 },
  { id: 'tx2', date: '24 ноя, 11:20', description: 'Оплата в буфете · Выпечка и морс', amountRub: -120 },
  { id: 'tx3', date: '22 ноя, 19:04', description: 'Пополнение карты «Москвёнок» (СБП)', amountRub: +2000 },
  { id: 'tx4', date: '20 ноя, 10:21', description: 'Оплата в буфете · Горячий завтрак', amountRub: -195 },
];
