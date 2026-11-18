package ru.mesh.foss

/** Application copy in Russian, English, Hungarian, Spanish and French. */
internal enum class Words(
    private val ru: String,
    private val en: String,
    private val hu: String,
    private val es: String,
    private val fr: String
) {
    ScheduleTab("Расписание", "Schedule", "Órarend", "Horario", "Agenda"),
    Email("Эл. почта", "Email", "E-mail", "Correo electrónico", "E-mail"),
    OpaqueToken("Непрозрачный", "Opaque", "Átlátszatlan", "Opaco", "Opaque"),
    MyTheme(
        "Моя тема",
        "My theme",
        "Saját téma",
        "Mi tema",
        "Mon thème"
    ),
    Midnight(
        "Полночь",
        "Midnight",
        "Éjfél",
        "Medianoche",
        "Minuit"
    ),
    Ocean(
        "Океан",
        "Ocean",
        "Óceán",
        "Océano",
        "Océan"
    ),
    Forest(
        "Лес",
        "Forest",
        "Erdő",
        "Bosque",
        "Forêt"
    ),
    Sunset(
        "Закат",
        "Sunset",
        "Naplemente",
        "Atardecer",
        "Crépuscule"
    ),
    Light(
        "Светлая",
        "Light",
        "Világos",
        "Claro",
        "Clair"
    ),
    SystemColours(
        "Динамическая (M3E)",
        "System colours",
        "Rendszerszínek",
        "Colores del sistema",
        "Couleurs du système"
    ),
    Lavender(
        "Лаванда",
        "Lavender",
        "Levendula",
        "Lavanda",
        "Lavande"
    ),
    Amber(
        "Янтарь",
        "Amber",
        "Borostyán",
        "Ámbar",
        "Ambre"
    ),
    Glacier(
        "Ледник",
        "Glacier",
        "Gleccser",
        "Glaciar",
        "Glacier"
    ),
    CherryBlossom(
        "Сакура",
        "Cherry blossom",
        "Cseresznyevirág",
        "Flor de cerezo",
        "Fleur de cerisier"
    ),
    Graphite(
        "Графит",
        "Graphite",
        "Grafit",
        "Grafito",
        "Graphite"
    ),
    UpToDate(
        "Обновлений нет",
        "Up to date",
        "Nincs frissítés",
        "No hay actualizaciones",
        "Aucune mise à jour"
    ),
    Appearance(
        "Оформление",
        "Appearance",
        "Megjelenés",
        "Apariencia",
        "Apparence"
    ),
    ThemeName(
        "Название своей темы",
        "Theme name",
        "Téma neve",
        "Nombre del tema",
        "Nom du thème"
    ),
    AccentBackgroundSurfaceHex(
        "Акцент, фон, поверхность (HEX)",
        "Accent, background, surface (HEX)",
        "Kiemelés, háttér, felület (HEX)",
        "Acento, fondo, superficie (HEX)",
        "Accent, fond, surface (HEX)"
    ),
    CustomPaletteExample(
        "Пример: 67E8F9,071923,102A3A",
        "Example: 67E8F9,071923,102A3A",
        "Példa: 67E8F9,071923,102A3A",
        "Ejemplo: 67E8F9,071923,102A3A",
        "Exemple : 67E8F9,071923,102A3A"
    ),
    ApplyTheme(
        "Применить свою тему",
        "Apply theme",
        "Téma alkalmazása",
        "Aplicar tema",
        "Appliquer le thème"
    ),
    Language(
        "Язык",
        "Language",
        "Nyelv",
        "Idioma",
        "Langue"
    ),
    Application(
        "Приложение",
        "Application",
        "Alkalmazás",
        "Aplicación",
        "Application"
    ),
    Server(
        "Сервер",
        "Server",
        "Kiszolgáló",
        "Servidor",
        "Serveur"
    ),
    Storage(
        "Хранилище",
        "Storage",
        "Tárhely",
        "Almacenamiento",
        "Stockage"
    ),
    Encrypted(
        "зашифровано",
        "encrypted",
        "titkosított",
        "cifrado",
        "chiffré"
    ),
    Private(
        "приватное",
        "private",
        "privát",
        "privado",
        "privé"
    ),
    NameCache(
        "Кэш имён",
        "Name cache",
        "Névgyorsítótár",
        "Caché de nombres",
        "Cache des noms"
    ),
    Enabled(
        "включён",
        "enabled",
        "bekapcsolva",
        "activada",
        "activé"
    ),
    Vibration(
        "Вибрация",
        "Vibration",
        "Rezgés",
        "Vibración",
        "Vibration"
    ),
    UpdateChannel(
        "Канал обновлений",
        "Update channel",
        "Frissítési csatorna",
        "Canal de actualizaciones",
        "Canal de mise à jour"
    ),
    Stable(
        "Релизы",
        "Stable",
        "Stabil",
        "Estables",
        "Stables"
    ),
    PreReleases(
        "Пререлизы",
        "Pre-releases",
        "Előzetes",
        "Preliminares",
        "Préversions"
    ),
    IncludesBetaRcAndNightlyBuildsCheckManually(
        "Пререлизы — nightly из main и beta/rc. Nightly без авто-предупреждений.",
        "Includes beta, RC and nightly builds. Check manually for nightly updates.",
        "Béta-, RC- és éjszakai kiadások. Az éjszakai frissítéseket kézzel keresheti.",
        "Incluye versiones beta, RC y nocturnas. Busca las nocturnas manualmente.",
        "Inclut les versions bêta, RC et nocturnes. Recherchez manuellement les versions nocturnes."
    ),
    CheckForUpdates(
        "Проверить обновления сейчас",
        "Check for updates",
        "Frissítések keresése",
        "Buscar actualizaciones",
        "Rechercher des mises à jour"
    ),
    MeshToken(
        "Токен МЭШ",
        "MESH token",
        "MESH-token",
        "Token de MESH",
        "Jeton MESH"
    ),
    MaskedToken(
        "Маска",
        "Masked token",
        "Maszkolt token",
        "Token oculto",
        "Jeton masqué"
    ),
    Type(
        "Тип",
        "Type",
        "Típus",
        "Tipo",
        "Type"
    ),
    Length(
        "Длина",
        "Length",
        "Hossz",
        "Longitud",
        "Longueur"
    ),
    Expires(
        "Истекает",
        "Expires",
        "Lejárat",
        "Caduca",
        "Expiration"
    ),
    MeshCouldNotRenewYourTokenSignIn(
        "МЭШ не выдал новый токен. Возможно, сессия завершена — войдите заново.",
        "MESH could not renew your token. Sign in again.",
        "A MESH nem újította meg a tokent. Jelentkezzen be újra.",
        "MESH no pudo renovar el token. Vuelve a iniciar sesión.",
        "MESH n’a pas pu renouveler le jeton. Reconnectez-vous."
    ),
    TokenRefreshed(
        "Токен обновлён",
        "Token refreshed",
        "Token frissítve",
        "Token renovado",
        "Jeton renouvelé"
    ),
    RefreshToken(
        "Обновить токен",
        "Refresh token",
        "Token frissítése",
        "Renovar token",
        "Renouveler le jeton"
    ),
    Account(
        "Аккаунт",
        "Account",
        "Fiók",
        "Cuenta",
        "Compte"
    ),
    ProfileLoaded(
        "Профиль загружен",
        "Profile loaded",
        "Profil betöltve",
        "Perfil cargado",
        "Profil chargé"
    ),
    ProfileNotLoadedYet(
        "Профиль ещё не загружен",
        "Profile not loaded yet",
        "A profil még nincs betöltve",
        "Perfil pendiente de carga",
        "Profil en attente de chargement"
    ),
    SignOutAndDeleteToken(
        "Выйти и удалить токен",
        "Sign out and delete token",
        "Kijelentkezés és a token törlése",
        "Cerrar sesión y eliminar el token",
        "Se déconnecter et supprimer le jeton"
    ),
    UpdateAvailable(
        "Доступно обновление",
        "Update available",
        "Frissítés érhető el",
        "Actualización disponible",
        "Mise à jour disponible"
    ),
    UpdateVersionAvailable(
        "На GitHub уже есть версия %s. Можно скачать и установить её.",
        "Version %s is ready to download on GitHub.",
        "A(z) %s verzió letölthető a GitHubról.",
        "La versión %s está disponible en GitHub.",
        "La version %s est disponible sur GitHub."
    ),
    Download(
        "Скачать",
        "Download",
        "Letöltés",
        "Descargar",
        "Télécharger"
    ),
    Later(
        "Позже",
        "Later",
        "Később",
        "Más tarde",
        "Plus tard"
    ),
    Profile(
        "Профиль",
        "Profile",
        "Profil",
        "Perfil",
        "Profil"
    ),
    SignOut(
        "Выйти",
        "Sign out",
        "Kijelentkezés",
        "Cerrar sesión",
        "Se déconnecter"
    ),
    Retry(
        "Повторить",
        "Retry",
        "Újra",
        "Reintentar",
        "Réessayer"
    ),
    Student(
        "Ученик",
        "Student",
        "Tanuló",
        "Alumno",
        "Élève"
    ),
    FullName(
        "ФИО",
        "Full name",
        "Teljes név",
        "Nombre completo",
        "Nom complet"
    ),
    Class(
        "Класс",
        "Class",
        "Osztály",
        "Clase",
        "Classe"
    ),
    School(
        "Школа",
        "School",
        "Iskola",
        "Escuela",
        "École"
    ),
    Phone(
        "Телефон",
        "Phone",
        "Telefon",
        "Teléfono",
        "Téléphone"
    ),
    Children(
        "Дети",
        "Children",
        "Gyermekek",
        "Hijos",
        "Enfants"
    ),
    Parent(
        "Родитель",
        "Parent",
        "Szülő",
        "Progenitor",
        "Parent"
    ),
    Expired(
        " (истёк)",
        " (expired)",
        " (lejárt)",
        " (caducado)",
        " (expiré)"
    ),
    Close(
        "Закрыть",
        "Close",
        "Bezárás",
        "Cerrar",
        "Fermer"
    ),
    Grade(
        "Оценка",
        "Grade",
        "Jegy",
        "Nota",
        "Note"
    ),
    Teacher(
        "Учитель",
        "Teacher",
        "Tanár",
        "Docente",
        "Enseignant"
    ),
    Topic(
        "Тема",
        "Topic",
        "Téma",
        "Tema",
        "Sujet"
    ),
    Comment(
        "Комментарий",
        "Comment",
        "Megjegyzés",
        "Comentario",
        "Commentaire"
    ),
    ClassResultsStudents(
        "Результаты по классу (%s учеников)",
        "Class results · %s students",
        "Osztályeredmények · %s tanuló",
        "Resultados de clase · %s alumnos",
        "Résultats de la classe · %s élèves"
    ),
    LoadingStudentProfile(
        "Загружаем профиль ученика…",
        "Loading student profile…",
        "Tanulói profil betöltése…",
        "Cargando el perfil del alumno…",
        "Chargement du profil de l’élève…"
    ),
    DailyMenu(
        "Меню на день",
        "Daily menu",
        "Napi menü",
        "Menú del día",
        "Menu du jour"
    ),
    Paid(
        "Платное",
        "Paid",
        "Fizetős",
        "De pago",
        "Payant"
    ),
    Subsidised(
        "Льготное",
        "Subsidised",
        "Támogatott",
        "Subvencionado",
        "Subventionné"
    ),
    NoMenuForThisDay(
        "Меню на этот день недоступно.",
        "No menu for this day.",
        "Ezen a napon nincs menü.",
        "No hay menú para este día.",
        "Aucun menu pour ce jour."
    ),
    PreOrders(
        "Предзаказы",
        "Pre-orders",
        "Előrendelések",
        "Pedidos anticipados",
        "Précommandes"
    ),
    Breakfast(
        "Завтрак",
        "Breakfast",
        "Reggeli",
        "Desayuno",
        "Petit-déjeuner"
    ),
    MorningSnack(
        "Второй завтрак",
        "Morning snack",
        "Tízórai",
        "Almuerzo de media mañana",
        "Collation du matin"
    ),
    Lunch(
        "Обед",
        "Lunch",
        "Ebéd",
        "Comida",
        "Déjeuner"
    ),
    AfternoonSnack(
        "Полдник",
        "Afternoon snack",
        "Uzsonna",
        "Merienda",
        "Goûter"
    ),
    Dinner(
        "Ужин",
        "Dinner",
        "Vacsora",
        "Cena",
        "Dîner"
    ),
    Meal(
        "Приём пищи",
        "Meal",
        "Étkezés",
        "Comida",
        "Repas"
    ),
    AccountBalance(
        "Баланс лицевого счёта",
        "Account balance",
        "Számlaegyenleg",
        "Saldo de la cuenta",
        "Solde du compte"
    ),
    Unavailable(
        "Недоступен",
        "Unavailable",
        "Nem elérhető",
        "No disponible",
        "Indisponible"
    ),
    DailyLimit(
        "Лимит в день: ",
        "Daily limit: ",
        "Napi keret: ",
        "Límite diario: ",
        "Plafond quotidien : "
    ),
    PreOrder(
        "Предзаказ",
        "Pre-order",
        "Előrendelés",
        "Pedido anticipado",
        "Précommande"
    ),
    SubsidisedMeals(
        "Льготное питание",
        "Subsidised meals",
        "Támogatott étkezés",
        "Comidas subvencionadas",
        "Repas subventionnés"
    ),
    Available(
        "доступно",
        "available",
        "elérhető",
        "disponible",
        "disponible"
    ),
    UnavailableLabel(
        "недоступно",
        "unavailable",
        "nem elérhető",
        "no disponible",
        "indisponible"
    ),
    Mon(
        "Пн",
        "Mon",
        "H",
        "Lun",
        "Lun"
    ),
    Tue(
        "Вт",
        "Tue",
        "K",
        "Mar",
        "Mar"
    ),
    Wed(
        "Ср",
        "Wed",
        "Sze",
        "Mié",
        "Mer"
    ),
    Thu(
        "Чт",
        "Thu",
        "Cs",
        "Jue",
        "Jeu"
    ),
    Fri(
        "Пт",
        "Fri",
        "P",
        "Vie",
        "Ven"
    ),
    Sat(
        "Сб",
        "Sat",
        "Szo",
        "Sáb",
        "Sam"
    ),
    Sun(
        "Вс",
        "Sun",
        "V",
        "Dom",
        "Dim"
    ),
    G(
        "г",
        "g",
        "g",
        "g",
        "g"
    ),
    Kcal(
        "ккал",
        "kcal",
        "kcal",
        "kcal",
        "kcal"
    ),
    Dishes(
        "блюд: ",
        "dishes: ",
        "ételek: ",
        "platos: ",
        "plats : "
    ),
    Order(
        "Заказ",
        "Order",
        "Rendelés",
        "Pedido",
        "Commande"
    ),
    ByDate(
        "По дате",
        "By date",
        "Dátum szerint",
        "Por fecha",
        "Par date"
    ),
    BySubject(
        "По предмету",
        "By subject",
        "Tantárgy szerint",
        "Por asignatura",
        "Par matière"
    ),
    FinalGrades(
        "Итоговые",
        "Final grades",
        "Zárójegyek",
        "Notas finales",
        "Notes finales"
    ),
    NoGradedSubjects(
        "Предметов с оценками нет",
        "No graded subjects",
        "Nincs osztályozott tantárgy",
        "No hay asignaturas con notas",
        "Aucune matière avec des notes"
    ),
    NoGradesInThisPeriod(
        "Оценок за период нет",
        "No grades in this period",
        "Nincs jegy ebben az időszakban",
        "No hay notas en este periodo",
        "Aucune note pour cette période"
    ),
    NoMoreGrades(
        "Больше оценок нет",
        "No more grades",
        "Nincs több jegy",
        "No hay más notas",
        "Fin des notes"
    ),
    GradeDistribution(
        "Распределение оценок",
        "Grade distribution",
        "Jegyek eloszlása",
        "Distribución de notas",
        "Répartition des notes"
    ),
    NumberOfGradesByScore(
        "Число оценок каждой отметки",
        "Number of grades by score",
        "Jegyek száma pontszámonként",
        "Cantidad de notas por puntuación",
        "Nombre de notes par résultat"
    ),
    NotEnoughDataForAChart(
        "Недостаточно данных для построения графика",
        "Not enough data for a chart",
        "Nincs elég adat a grafikonhoz",
        "Datos insuficientes para el gráfico",
        "Données insuffisantes pour le graphique"
    ),
    WeeklyTrend(
        "Динамика по неделям",
        "Weekly trend",
        "Heti alakulás",
        "Evolución semanal",
        "Évolution hebdomadaire"
    ),
    WeeklyAverageScaleFromTo(
        "Средний балл за неделю · шкала от 2 до 5",
        "Weekly average · scale from 2 to 5",
        "Heti átlag · 2–5 skála",
        "Media semanal · escala de 2 a 5",
        "Moyenne hebdomadaire · échelle de 2 à 5"
    ),
    NoGradesToPlotForThisPeriod(
        "За выбранный период нет оценок для графика",
        "No grades to plot for this period",
        "Nincs ábrázolható jegy ebben az időszakban",
        "No hay notas que mostrar en este periodo",
        "Aucune note à afficher pour cette période"
    ),
    FinalResults(
        "Итоговые оценки",
        "Final results",
        "Záróeredmények",
        "Calificaciones finales",
        "Résultats finaux"
    ),
    QuarterlyAndAnnualGradesBySubject(
        "Четверти и годовая по предметам",
        "Quarterly and annual grades by subject",
        "Negyedéves és éves jegyek tantárgyanként",
        "Notas trimestrales y anuales por asignatura",
        "Notes trimestrielles et annuelles par matière"
    ),
    SchoolYear(
        "Учебный год",
        "School year",
        "Tanév",
        "Curso escolar",
        "Année scolaire"
    ),
    Current(
        "%s · текущий",
        "%s · current",
        "%s · aktuális",
        "%s · actual",
        "%s · en cours"
    ),
    NoFinalGradesForThisYear(
        "Итоговых оценок за этот год нет",
        "No final grades for this year",
        "Nincs zárójegy erre az évre",
        "No hay notas finales de este curso",
        "Aucune note finale pour cette année"
    ),
    FinalGradesHaveYetToBePosted(
        "За этот год итоговые оценки ещё не выставлены",
        "Final grades have yet to be posted",
        "Még nem adták meg a zárójegyeket",
        "Aún no se han publicado las notas finales",
        "Les notes finales ne sont pas encore publiées"
    ),
    Subject(
        "Предмет",
        "Subject",
        "Tantárgy",
        "Asignatura",
        "Matière"
    ),
    Year(
        "Год",
        "Year",
        "Év",
        "Año",
        "Année"
    ),
    SubjectNotFound(
        "Предмет не найден",
        "Subject not found",
        "A tantárgy nem található",
        "Asignatura no encontrada",
        "Matière introuvable"
    ),
    NoGradesForThisSubject(
        "Оценок по этому предмету нет",
        "No grades for this subject",
        "Nincs jegy ebből a tantárgyból",
        "No hay notas de esta asignatura",
        "Aucune note dans cette matière"
    ),
    Average(
        "Средний: %s",
        "Average: %s",
        "Átlag: %s",
        "Media: %s",
        "Moyenne : %s"
    ),
    Grades(
        "Оценок: %s",
        "Grades: %s",
        "Jegyek: %s",
        "Notas: %s",
        "Notes : %s"
    ),
    YearLabel(
        "Год: %s",
        "Year: %s",
        "Év: %s",
        "Año: %s",
        "Année : %s"
    ),
    AverageScore(
        "Средний",
        "Average",
        "Átlag",
        "Media",
        "Moyenne"
    ),
    GradeCount(
        "Оценок",
        "Grade count",
        "Jegyek száma",
        "Cantidad de notas",
        "Nombre de notes"
    ),
    TrendRising(
        "Динамика: рост",
        "Trend: rising",
        "Javuló tendencia",
        "Tendencia al alza",
        "Tendance à la hausse"
    ),
    TrendFalling(
        "Динамика: снижение",
        "Trend: falling",
        "Romló tendencia",
        "Tendencia a la baja",
        "Tendance à la baisse"
    ),
    Month(
        "Месяц",
        "Month",
        "Hónap",
        "Mes",
        "Mois"
    ),
    MyRank(
        "Моё место",
        "My rank",
        "Helyezésem",
        "Mi puesto",
        "Mon rang"
    ),
    Of(
        "из %s",
        "of %s",
        "összesen %s",
        "de %s",
        "sur %s"
    ),
    MyScore(
        "Мой балл",
        "My score",
        "Pontszámom",
        "Mi nota",
        "Mon score"
    ),
    Median(
        "медиана %s",
        "median %s",
        "medián %s",
        "mediana %s",
        "médiane %s"
    ),
    Overall(
        "Общий",
        "Overall",
        "Összesített",
        "General",
        "Général"
    ),
    NoDataForThisDate(
        "Нет данных за эту дату",
        "No data for this date",
        "Nincs adat ezen a napon",
        "No hay datos para esta fecha",
        "Aucune donnée à cette date"
    ),
    Unnamed(
        "Без имени",
        "Unnamed",
        "Névtelen",
        "Sin nombre",
        "Sans nom"
    ),
    Me(
        "это я",
        "me",
        "én",
        "yo",
        "moi"
    ),
    High(
        "сильный",
        "high",
        "magas",
        "alto",
        "élevé"
    ),
    AverageRank(
        "средний",
        "average",
        "közepes",
        "medio",
        "moyen"
    ),
    Rising(
        "рост",
        "rising",
        "emelkedik",
        "sube",
        "en hausse"
    ),
    Falling(
        "снижение",
        "falling",
        "csökken",
        "baja",
        "en baisse"
    ),
    StableLabel(
        "стабильно",
        "stable",
        "változatlan",
        "estable",
        "stable"
    ),
    NoData(
        "нет данных",
        "no data",
        "nincs adat",
        "sin datos",
        "aucune donnée"
    ),
    NoLessons(
        "Нет уроков",
        "No lessons",
        "Nincs tanóra",
        "No hay clases",
        "Aucun cours"
    ),
    Lesson(
        "%s урок",
        "Lesson %s",
        "%s. óra",
        "Clase %s",
        "Cours %s"
    ),
    LessonLabel(
        "Урок",
        "Lesson",
        "Tanóra",
        "Clase",
        "Cours"
    ),
    Homework(
        "ДЗ: %s",
        "Homework: %s",
        "Házi feladat: %s",
        "Tarea: %s",
        "Devoir : %s"
    ),
    Ranking(
        "Рейтинг",
        "Ranking",
        "Rangsor",
        "Clasificación",
        "Classement"
    ),
    GradesTab(
        "Оценки",
        "Grades",
        "Jegyek",
        "Notas",
        "Notes"
    ),
    HomeworkLabel(
        "Домашние задания",
        "Homework",
        "Házi feladatok",
        "Tareas",
        "Devoirs"
    ),
    Schedule(
        "Расписание",
        "Schedule",
        "Órarend",
        "Horario",
        "Emploi du temps"
    ),
    MealsAndBalance(
        "Еда и баланс",
        "Meals and balance",
        "Étkezések és egyenleg",
        "Comidas y saldo",
        "Repas et solde"
    ),
    Settings(
        "Настройки",
        "Settings",
        "Beállítások",
        "Ajustes",
        "Réglages"
    ),
    MeshRanking(
        "МЭШ Рейтинг",
        "MESH Ranking",
        "MESH-rangsor",
        "Clasificación MESH",
        "Classement MESH"
    ),
    SettingsAndProfile(
        "Настройки и профиль",
        "Settings and profile",
        "Beállítások és profil",
        "Ajustes y perfil",
        "Réglages et profil"
    ),
    Tasks(
        "ДЗ",
        "Tasks",
        "Feladatok",
        "Tareas",
        "Devoirs"
    ),
    Meals(
        "Еда",
        "Meals",
        "Étkezés",
        "Comidas",
        "Repas"
    ),
    SignInToMesh(
        "Вход в МЭШ",
        "Sign in to MESH",
        "MESH-bejelentkezés",
        "Acceder a MESH",
        "Connexion à MESH"
    ),
    SignInOnTheMeshWebsite(
        "Способ 2. Войти через сайт МЭШ",
        "Sign in on the MESH website",
        "Bejelentkezés a MESH weboldalán",
        "Acceder desde la web de MESH",
        "Connexion sur le site MESH"
    ),
    SignInAtSchoolMosRuThisWindow(
        "Откроется school.mos.ru. После входа приложение перехватит Authorization-токен и само закроет окно.",
        "Sign in at school.mos.ru. This window closes when the app receives your token.",
        "Jelentkezzen be a school.mos.ru oldalon. Az ablak bezárul, amikor az alkalmazás megkapja a tokent.",
        "Accede a school.mos.ru. La ventana se cerrará cuando la aplicación reciba tu token.",
        "Connectez-vous sur school.mos.ru. La fenêtre se fermera à la réception du jeton."
    ),
    OpenSchoolMosRu(
        "Открыть school.mos.ru",
        "Open school.mos.ru",
        "A school.mos.ru megnyitása",
        "Abrir school.mos.ru",
        "Ouvrir school.mos.ru"
    ),
    EnterATokenManually(
        "Способ 3. Вставить токен вручную",
        "Enter a token manually",
        "Token kézi megadása",
        "Introducir el token manualmente",
        "Saisir le jeton manuellement"
    ),
    Open(
        "Откройте: %s",
        "Open: %s",
        "Megnyitás: %s",
        "Abre: %s",
        "Ouvrez : %s"
    ),
    CopyTheTokenFromThePageAndPaste(
        "Скопируйте токен со страницы и вставьте сюда",
        "Copy the token from the page and paste it here",
        "Másolja a tokent az oldalról, és illessze be ide",
        "Copia el token de la página y pégalo aquí",
        "Copiez le jeton de la page et collez-le ici"
    ),
    SignInWithToken(
        "Войти с токеном",
        "Sign in with token",
        "Bejelentkezés tokennel",
        "Acceder con token",
        "Se connecter avec le jeton"
    ),
    Subjects(
        "Предметы",
        "Subjects",
        "Tantárgyak",
        "Asignaturas",
        "Matières"
    ),
    NoSubjectsFound(
        "Предметы не найдены",
        "No subjects found",
        "Nem találhatók tantárgyak",
        "No se encontraron asignaturas",
        "Aucune matière trouvée"
    ),
    Rank(
        "место #%s",
        "rank #%s",
        "%s. hely",
        "puesto %s",
        "rang %s"
    ),
    Exam(
        "экзамен",
        "exam",
        "vizsga",
        "examen",
        "examen"
    ),
    Cancel(
        "Отмена",
        "Cancel",
        "Mégse",
        "Cancelar",
        "Annuler"
    ),
    Today(
        "Сегодня",
        "Today",
        "Ma",
        "Hoy",
        "Aujourd’hui"
    ),
    Tomorrow(
        "Завтра",
        "Tomorrow",
        "Holnap",
        "Mañana",
        "Demain"
    ),
    Yesterday(
        "Вчера",
        "Yesterday",
        "Tegnap",
        "Ayer",
        "Hier"
    ),
    DayAfterTomorrow(
        "Послезавтра",
        "Day after tomorrow",
        "Holnapután",
        "Pasado mañana",
        "Après-demain"
    ),
    DayBeforeYesterday(
        "Позавчера",
        "Day before yesterday",
        "Tegnapelőtt",
        "Anteayer",
        "Avant-hier"
    ),
    DueOnDate(
        "На день",
        "Due on date",
        "Határidő szerint",
        "Por entrega",
        "À rendre"
    ),
    Assigned(
        "Задано",
        "Assigned",
        "Kiadás szerint",
        "Asignadas",
        "Donnés"
    ),
    NoHomeworkDueToday(
        "На этот день домашнего задания нет",
        "No homework due today",
        "Erre a napra nincs házi feladat",
        "No hay tareas para este día",
        "Aucun devoir à rendre ce jour"
    ),
    NoHomeworkAssignedOnThisDay(
        "В этот день ничего не задавали",
        "No homework assigned on this day",
        "Ezen a napon nem adtak házi feladatot",
        "No se asignaron tareas ese día",
        "Aucun devoir donné ce jour"
    ),
    Assignment(
        "Задание",
        "Assignment",
        "Feladat",
        "Tarea",
        "Devoir"
    ),
    Due(
        "до %s",
        "due %s",
        "határidő: %s",
        "entrega: %s",
        "pour le %s"
    ),
    AssignmentCopied(
        "Текст задания скопирован",
        "Assignment copied",
        "Feladat másolva",
        "Tarea copiada",
        "Devoir copié"
    ),
    Copy(
        "Копировать",
        "Copy",
        "Másolás",
        "Copiar",
        "Copier"
    ),
    AssignedLabel(
        "Задано: %s",
        "Assigned: %s",
        "Kiadva: %s",
        "Asignada: %s",
        "Donné le %s"
    ),
    DueLabel(
        "Сдать до: %s",
        "Due: %s",
        "Határidő: %s",
        "Entrega: %s",
        "À rendre le %s"
    ),
    MaterialsAndDigitalHomework(
        "Материалы и ЦДЗ",
        "Materials and digital homework",
        "Anyagok és digitális feladatok",
        "Materiales y tareas digitales",
        "Documents et devoirs numériques"
    ),
    Material(
        "Материал",
        "Material",
        "Tananyag",
        "Material",
        "Document"
    ),
    Opening(
        "Открываем…",
        "Opening…",
        "Megnyitás…",
        "Abriendo…",
        "Ouverture…"
    ),
    TapToOpen(
        "%s · нажмите, чтобы открыть",
        "%s · tap to open",
        "%s · koppintson a megnyitáshoz",
        "%s · toca para abrir",
        "%s · touchez pour ouvrir"
    ),
    TapToOpenLabel(
        "Нажмите, чтобы открыть",
        "Tap to open",
        "Koppintson a megnyitáshoz",
        "Toca para abrir",
        "Touchez pour ouvrir"
    ),
    Files(
        "Файлы",
        "Files",
        "Fájlok",
        "Archivos",
        "Fichiers"
    ),
    File(
        "Файл",
        "File",
        "Fájl",
        "Archivo",
        "Fichier"
    ),
    CouldNotOpenLink(
        "Не удалось открыть ссылку",
        "Could not open link",
        "A hivatkozás nem nyitható meg",
        "No se pudo abrir el enlace",
        "Impossible d’ouvrir le lien"
    ),
    ConfirmationTimedOut(
        "Время ожидания подтверждения истекло",
        "Confirmation timed out",
        "Lejárt a jóváhagyási idő",
        "Se agotó el tiempo de confirmación",
        "Le délai de confirmation a expiré"
    ),
    MeshDidNotIssueATokenCheckYour(
        "МЭШ не выдал токен. Возможно, нужен код из письма или подтверждение доверия браузеру.",
        "MESH did not issue a token. Check your email or confirm browser trust.",
        "A MESH nem adott tokent. Ellenőrizze az e-mailt vagy jelölje megbízhatónak a böngészőt.",
        "MESH no emitió un token. Revisa el correo o confirma la confianza en el navegador.",
        "MESH n’a pas émis de jeton. Vérifiez vos e-mails ou autorisez le navigateur."
    ),
    MeshDidNotReturnAQrLinkTry(
        "МЭШ не выдал QR-ссылку. Попробуйте ещё раз.",
        "MESH did not return a QR link. Try again.",
        "Nem érkezett QR-hivatkozás. Próbálja újra.",
        "MESH no devolvió un enlace QR. Inténtalo de nuevo.",
        "Aucun lien QR reçu. Réessayez."
    ),
    MissingCommand(
        "нет command",
        "missing command",
        "hiányzó parancs",
        "falta el comando",
        "commande manquante"
    ),
    QrSignInUnavailableTryAgain(
        "МЭШ не включил QR-вход (ответ: %s). Попробуйте ещё раз.",
        "QR sign-in unavailable (%s). Try again.",
        "A QR-belépés nem érhető el (%s). Próbálja újra.",
        "Acceso QR no disponible (%s). Inténtalo de nuevo.",
        "Connexion QR indisponible (%s). Réessayez."
    ),
    OpenTheLinkAndApproveSignInIn(
        "Откройте ссылку и подтвердите вход в приложении МЭШ.",
        "Open the link and approve sign-in in MESH.",
        "Nyissa meg a hivatkozást és hagyja jóvá a belépést a MESH-ben.",
        "Abre el enlace y confirma el acceso en MESH.",
        "Ouvrez le lien et confirmez la connexion dans MESH."
    ),
    CodeScannedApproveSignInInMesh(
        "Код отсканирован — подтвердите вход в приложении МЭШ.",
        "Code scanned. Approve sign-in in MESH.",
        "Kód beolvasva. Hagyja jóvá a belépést a MESH-ben.",
        "Código escaneado. Confirma el acceso en MESH.",
        "Code scanné. Confirmez la connexion dans MESH."
    ),
    ApprovedSigningIn(
        "Подтверждено, завершаем вход...",
        "Approved. Signing in…",
        "Jóváhagyva. Bejelentkezés…",
        "Confirmado. Accediendo…",
        "Confirmation reçue. Connexion…"
    ),
    LinkExpiredRequestingAnother(
        "Ссылка истекла, запрашиваем новую...",
        "Link expired. Requesting another…",
        "A hivatkozás lejárt. Új igénylése…",
        "Enlace caducado. Solicitando otro…",
        "Lien expiré. Demande d’un nouveau lien…"
    ),
    SignInNotConfirmedTryAgain(
        "МЭШ ещё не подтвердил вход. Попробуйте ещё раз.",
        "Sign-in not confirmed. Try again.",
        "A belépés nincs jóváhagyva. Próbálja újra.",
        "Acceso sin confirmar. Inténtalo de nuevo.",
        "Connexion non confirmée. Réessayez."
    ),
    EnterTheCodeEmailedByMesh(
        "Нужен код из письма МЭШ",
        "Enter the code emailed by MESH",
        "Adja meg a MESH e-mailben küldött kódját",
        "Introduce el código del correo de MESH",
        "Saisissez le code envoyé par MESH"
    ),
    CodeEntryCancelled(
        "Ввод кода отменён",
        "Code entry cancelled",
        "Kódbevitel megszakítva",
        "Introducción del código cancelada",
        "Saisie du code annulée"
    ),
    BrowserTrustWasRejected(
        "МЭШ не принял подтверждение «доверять браузеру»",
        "Browser trust was rejected",
        "A böngésző megbízhatóságának igazolása sikertelen",
        "Se rechazó la confianza en el navegador",
        "L’autorisation du navigateur a échoué"
    ),
    AuthorisingBrowser(
        "Подтверждаем доверие браузеру...",
        "Authorising browser…",
        "Böngésző engedélyezése…",
        "Autorizando navegador…",
        "Autorisation du navigateur…"
    ),
    NoTokenReceivedFromSchoolMosRu(
        "school.mos.ru не выдал токен",
        "No token received from school.mos.ru",
        "Nem érkezett token a school.mos.ru oldalról",
        "No se recibió el token de school.mos.ru",
        "Aucun jeton reçu de school.mos.ru"
    ),
    UnexpectedMeshPage(
        "Неожиданная страница МЭШ: %s",
        "Unexpected MESH page: %s",
        "Váratlan MESH-oldal: %s",
        "Página inesperada de MESH: %s",
        "Page MESH inattendue : %s"
    ),
    CouldNotCompleteQrSignIn(
        "Не удалось завершить вход по QR",
        "Could not complete QR sign-in",
        "Nem sikerült befejezni a QR-belépést",
        "No se pudo completar el acceso QR",
        "Impossible de terminer la connexion QR"
    ),
    PreparingQrSession(
        "Готовим QR-сессию...",
        "Preparing QR session…",
        "QR-munkamenet előkészítése…",
        "Preparando sesión QR…",
        "Préparation de la session QR…"
    ),
    EnterYourEmailCode(
        "Введите код из письма МЭШ",
        "Enter your email code",
        "Adja meg az e-mailben kapott kódot",
        "Introduce el código del correo",
        "Saisissez le code reçu par e-mail"
    ),
    QrSignInFailed(
        "Не удалось выполнить QR-вход",
        "QR sign-in failed",
        "Sikertelen QR-belépés",
        "Falló el acceso QR",
        "Échec de la connexion QR"
    ),
    RestoringSavedSession(
        "Восстанавливаем сессию по сохранённым кукам...",
        "Restoring saved session…",
        "Mentett munkamenet visszaállítása…",
        "Restaurando la sesión guardada…",
        "Restauration de la session enregistrée…"
    ),
    SessionExpiredSignInByQr(
        "Сохранённая сессия истекла — войдите по QR.",
        "Session expired. Sign in by QR.",
        "A munkamenet lejárt. Lépjen be QR-kóddal.",
        "La sesión ha caducado. Accede con QR.",
        "Session expirée. Connectez-vous par QR."
    ),
    CouldNotRestoreSessionSignInByQr(
        "Не удалось восстановить сессию — войдите по QR.",
        "Could not restore session. Sign in by QR.",
        "A munkamenet nem állítható vissza. Lépjen be QR-kóddal.",
        "No se pudo restaurar la sesión. Accede con QR.",
        "Restauration impossible. Connectez-vous par QR."
    ),
    SignInWithAQrLink(
        "Способ 1. Вход по QR-ссылке",
        "Sign in with a QR link",
        "Belépés QR-hivatkozással",
        "Acceder con enlace QR",
        "Connexion par lien QR"
    ),
    ASavedSessionLetsYouSignInAutomatically(
        "Приложение войдёт само по сохранённой сессии, если куки от прошлого входа ещё живы. ",
        "A saved session lets you sign in automatically. ",
        "Az érvényes mentett munkamenet automatikusan belépteti. ",
        "Una sesión guardada permite acceder automáticamente. ",
        "Une session enregistrée permet la connexion automatique. "
    ),
    OtherwiseRequestALinkOpenItAndApprove(
        "Иначе нажмите «Получить ссылку», откройте её и подтвердите вход.",
        "Otherwise request a link, open it and approve sign-in.",
        "Egyébként kérjen hivatkozást, nyissa meg, majd hagyja jóvá a belépést.",
        "Si no, solicita un enlace, ábrelo y confirma el acceso.",
        "Sinon, demandez un lien, ouvrez-le et confirmez la connexion."
    ),
    RestoringSession(
        "Восстанавливаем сессию...",
        "Restoring session…",
        "Munkamenet visszaállítása…",
        "Restaurando sesión…",
        "Restauration de la session…"
    ),
    WaitingForConfirmation(
        "Ожидание подтверждения...",
        "Waiting for confirmation…",
        "Várakozás a jóváhagyásra…",
        "Esperando confirmación…",
        "En attente de confirmation…"
    ),
    GetLink(
        "Получить ссылку",
        "Get link",
        "Hivatkozás kérése",
        "Obtener enlace",
        "Obtenir un lien"
    ),
    OpenLink(
        "Открыть ссылку",
        "Open link",
        "Hivatkozás megnyitása",
        "Abrir enlace",
        "Ouvrir le lien"
    ),
    CancelQrSignIn(
        "Отменить QR-вход",
        "Cancel QR sign-in",
        "QR-belépés megszakítása",
        "Cancelar acceso QR",
        "Annuler la connexion QR"
    ),
    EmailCode(
        "Код из письма",
        "Email code",
        "E-mailben kapott kód",
        "Código del correo",
        "Code reçu par e-mail"
    ),
    Confirm(
        "Подтвердить",
        "Confirm",
        "Megerősítés",
        "Confirmar",
        "Confirmer"
    ),
    NoStudentInThisProfileSignInAgain(
        "В профиле МЭШ не найден ученик. Войдите заново.",
        "No student in this profile. Sign in again.",
        "Nincs tanuló a profilban. Jelentkezzen be újra.",
        "Este perfil no contiene alumnos. Vuelve a acceder.",
        "Aucun élève dans ce profil. Reconnectez-vous."
    ),
    NoStudentFoundSignInAsAParent(
        "В профиле МЭШ не найден ученик. Проверьте, что вы вошли как родитель.",
        "No student found. Sign in as a parent.",
        "Nem található tanuló. Jelentkezzen be szülőként.",
        "No se encontró ningún alumno. Accede como progenitor.",
        "Aucun élève trouvé. Connectez-vous comme parent."
    ),
    CouldNotLoadProfile(
        "Не удалось загрузить профиль",
        "Could not load profile",
        "A profil betöltése sikertelen",
        "No se pudo cargar el perfil",
        "Impossible de charger le profil"
    ),
    LoadingMeshProfile(
        "Загружаем профиль МЭШ…",
        "Loading MESH profile…",
        "MESH-profil betöltése…",
        "Cargando perfil de MESH…",
        "Chargement du profil MESH…"
    ),
    CouldNotOpenProfile(
        "Не удалось открыть профиль",
        "Could not open profile",
        "A profil nem nyitható meg",
        "No se pudo abrir el perfil",
        "Impossible d’ouvrir le profil"
    ),
    SignInAgain(
        "Войти заново",
        "Sign in again",
        "Újbóli bejelentkezés",
        "Volver a acceder",
        "Se reconnecter"
    ),
    CannotReachSchoolMosRuCheckYourConnection(
        "Нет связи с school.mos.ru. Проверьте интернет и попробуйте ещё раз.",
        "Cannot reach school.mos.ru. Check your connection and retry.",
        "A school.mos.ru nem érhető el. Ellenőrizze a kapcsolatot, majd próbálja újra.",
        "No se puede conectar con school.mos.ru. Comprueba la conexión y reintenta.",
        "Connexion à school.mos.ru impossible. Vérifiez le réseau et réessayez."
    ),
    MeshTimedOutTryAgain(
        "Сервер МЭШ не ответил вовремя. Попробуйте ещё раз.",
        "MESH timed out. Try again.",
        "A MESH nem válaszolt időben. Próbálja újra.",
        "MESH tardó demasiado. Inténtalo de nuevo.",
        "Le délai de réponse de MESH a expiré. Réessayez."
    ),
    InvalidOrExpiredTokenSignInAgain(
        "Токен недействителен или истёк. Выполните вход в МЭШ заново.",
        "Invalid or expired token. Sign in again.",
        "Érvénytelen vagy lejárt token. Jelentkezzen be újra.",
        "Token no válido o caducado. Vuelve a acceder.",
        "Jeton invalide ou expiré. Reconnectez-vous."
    ),
    AccessDeniedTrySigningInAsAParent(
        "Нет доступа к данным (403). Возможно, нужен вход под ролью родителя.",
        "Access denied (403). Try signing in as a parent.",
        "Hozzáférés megtagadva (403). Próbáljon szülőként belépni.",
        "Acceso denegado (403). Intenta acceder como progenitor.",
        "Accès refusé (403). Essayez un compte parent."
    ),
    UnavailableInYourRegion(
        "Метод недоступен для вашего региона (404).",
        "Unavailable in your region (404).",
        "A régiójában nem érhető el (404).",
        "No disponible en tu región (404).",
        "Indisponible dans votre région (404)."
    ),
    InvalidMeshTokenFormat(
        "Значение не похоже на токен МЭШ.",
        "Invalid MESH token format.",
        "Érvénytelen MESH-tokenformátum.",
        "Formato de token MESH no válido.",
        "Format de jeton MESH invalide."
    ),
    UnexpectedMeshResponseTrySigningInAgain(
        "МЭШ вернул неожиданный ответ. Возможно, требуется повторный вход.",
        "Unexpected MESH response. Try signing in again.",
        "Váratlan MESH-válasz. Jelentkezzen be újra.",
        "Respuesta inesperada de MESH. Vuelve a acceder.",
        "Réponse MESH inattendue. Reconnectez-vous."
    ),
    MeshServerErrorTryLater(
        "Ошибка сервера МЭШ (%s). Попробуйте позже.",
        "MESH server error (%s). Try later.",
        "MESH-kiszolgálóhiba (%s). Próbálja később.",
        "Error del servidor MESH (%s). Inténtalo más tarde.",
        "Erreur du serveur MESH (%s). Réessayez plus tard."
    ),
    UnknownError(
        "Неизвестная ошибка",
        "Unknown error",
        "Ismeretlen hiba",
        "Error desconocido",
        "Erreur inconnue"
    ),
    Period(
        "Период",
        "Period",
        "Időszak",
        "Periodo",
        "Période"
    ),
    Room(
        "каб. %s",
        "room %s",
        "%s. terem",
        "aula %s",
        "salle %s"
    ),
    MeshTokenReceived(
        "Токен МЭШ получен",
        "MESH token received",
        "MESH-token megérkezett",
        "Token de MESH recibido",
        "Jeton MESH reçu"
    ),
    StudentLabel(
        "Ученик ",
        "Student ",
        "Tanuló ",
        "Alumno ",
        "Élève "
    ),
    CouldNotCreateRequest(
        "Не удалось создать запрос: %s",
        "Could not create request: %s",
        "A kérés létrehozása sikertelen: %s",
        "No se pudo crear la solicitud: %s",
        "Impossible de créer la requête : %s"
    ),
    MeshRequestTimedOut(
        "Таймаут запроса к МЭШ",
        "MESH request timed out",
        "A MESH-kérés időtúllépése",
        "Se agotó el tiempo de la solicitud MESH",
        "Délai de la requête MESH dépassé"
    ),
    CannotReachSchoolMosRu(
        "Нет связи с school.mos.ru",
        "Cannot reach school.mos.ru",
        "A school.mos.ru nem érhető el",
        "Sin conexión con school.mos.ru",
        "Connexion à school.mos.ru impossible"
    ),
    NetworkError(
        "Ошибка сети: %s",
        "Network error: %s",
        "Hálózati hiba: %s",
        "Error de red: %s",
        "Erreur réseau : %s"
    ),
    TokenExpiredAndCouldNotBeRenewed(
        "Токен истёк, обновить не удалось",
        "Token expired and could not be renewed",
        "A lejárt token nem újítható meg",
        "El token caducó y no se pudo renovar",
        "Le jeton expiré n’a pas pu être renouvelé"
    ),
    Unauthorised(
        "Не авторизован (401)",
        "Unauthorised (401)",
        "Nincs bejelentkezve (401)",
        "Sin autorización (401)",
        "Non authentifié (401)"
    ),
    AccessDenied(
        "Доступ запрещён (403)",
        "Access denied (403)",
        "Hozzáférés megtagadva (403)",
        "Acceso denegado (403)",
        "Accès refusé (403)"
    ),
    MethodNotFound(
        "Метод не найден (404): %s",
        "Method not found (404): %s",
        "A metódus nem található (404): %s",
        "Método no encontrado (404): %s",
        "Méthode introuvable (404) : %s"
    ),
    MeshErrorHttp(
        "Ошибка МЭШ: HTTP %s",
        "MESH error: HTTP %s",
        "MESH-hiba: HTTP %s",
        "Error de MESH: HTTP %s",
        "Erreur MESH : HTTP %s"
    ),
    InvalidResponseFromMesh(
        "Не удалось разобрать ответ МЭШ",
        "Invalid response from MESH",
        "Érvénytelen MESH-válasz",
        "Respuesta de MESH no válida",
        "Réponse MESH invalide"
    ),
    RequestFailed(
        "Запрос не выполнен: %s",
        "Request failed: %s",
        "Sikertelen kérés: %s",
        "Solicitud fallida: %s",
        "Échec de la requête : %s"
    ),
    EmptyProfileResponse(
        "Пустой ответ профиля",
        "Empty profile response",
        "Üres profilválasz",
        "Respuesta de perfil vacía",
        "Réponse de profil vide"
    ),
    EmptyToken(
        "Пустой токен",
        "Empty token",
        "Üres token",
        "Token vacío",
        "Jeton vide"
    ),
    InvalidMeshToken(
        "Значение не похоже на токен МЭШ",
        "Invalid MESH token",
        "Érvénytelen MESH-token",
        "Token de MESH no válido",
        "Jeton MESH invalide"
    ),
    MissingHomework(
        "Не указано домашнее задание",
        "Missing homework",
        "Hiányzó házi feladat",
        "Falta la tarea",
        "Devoir manquant"
    ),
    CouldNotMarkHomeworkHttp(
        "Не удалось отметить ДЗ: HTTP %s",
        "Could not mark homework: HTTP %s",
        "A feladat jelölése sikertelen: HTTP %s",
        "No se pudo marcar la tarea: HTTP %s",
        "Impossible de marquer le devoir : HTTP %s"
    ),
    CouldNotMarkHomework(
        "Не удалось отметить ДЗ",
        "Could not mark homework",
        "A feladat jelölése sikertelen",
        "No se pudo marcar la tarea",
        "Impossible de marquer le devoir"
    ),
    MissingHomeworkMaterial(
        "Не указан материал домашнего задания",
        "Missing homework material",
        "Hiányzó feladatanyag",
        "Falta el material de la tarea",
        "Document du devoir manquant"
    ),
    CouldNotOpenMaterialHttp(
        "Не удалось открыть материал (HTTP %s)",
        "Could not open material (HTTP %s)",
        "A tananyag nem nyitható meg (HTTP %s)",
        "No se pudo abrir el material (HTTP %s)",
        "Impossible d’ouvrir le document (HTTP %s)"
    ),
    NoMaterialLinkReceived(
        "МЭШ не вернул ссылку на материал",
        "No material link received",
        "Nem érkezett hivatkozás a tananyaghoz",
        "No se recibió el enlace al material",
        "Aucun lien vers le document"
    ),
    CouldNotOpenMaterial(
        "Не удалось открыть материал",
        "Could not open material",
        "A tananyag nem nyitható meg",
        "No se pudo abrir el material",
        "Impossible d’ouvrir le document"
    ),
    SelectAStudentToViewMeals(
        "Не указан ученик для раздела питания",
        "Select a student to view meals",
        "Válasszon tanulót az étkezésekhez",
        "Selecciona un alumno para ver sus comidas",
        "Sélectionnez un élève pour voir ses repas"
    ),
    UnknownStudent(
        "Неизвестный ученик",
        "Unknown student",
        "Ismeretlen tanuló",
        "Alumno desconocido",
        "Élève inconnu"
    ),
    EmptyProfile(
        "Пустой профиль",
        "Empty profile",
        "Üres profil",
        "Perfil vacío",
        "Profil vide"
    ),
    MeshTokenRenewedAutomatically(
        "Токен МЭШ обновлён автоматически",
        "MESH token renewed automatically",
        "A MESH-token automatikusan megújult",
        "Token de MESH renovado automáticamente",
        "Jeton MESH renouvelé automatiquement"
    ),
    LocalDemoToken(
        "Демо-токен локального сервера",
        "Local demo token",
        "Helyi demótoken",
        "Token de demostración local",
        "Jeton de démonstration local"
    ),
    DemoModeLocalData(
        "демо-режим (локальные данные)",
        "Demo mode (local data)",
        "Demó mód (helyi adatok)",
        "Modo de demostración (datos locales)",
        "Mode démo (données locales)"
    ),
    NoNewTokenSignInAgain(
        "МЭШ не выдал новый токен — войдите заново",
        "No new token. Sign in again.",
        "Nem érkezett új token. Jelentkezzen be újra.",
        "No se recibió otro token. Vuelve a acceder.",
        "Aucun nouveau jeton. Reconnectez-vous."
    ),
    StudentNotFound(
        "Ученик не найден",
        "Student not found",
        "Tanuló nem található",
        "Alumno no encontrado",
        "Élève introuvable"
    ),
    BackgroundTokenRenewalFailed(
        "Не удалось обновить токен в фоне: %s",
        "Background token renewal failed: %s",
        "Háttérbeli tokenfrissítési hiba: %s",
        "Error al renovar el token en segundo plano: %s",
        "Échec du renouvellement en arrière-plan : %s"
    ),
    MeshSignInRequired(
        "Требуется вход в МЭШ",
        "MESH sign-in required",
        "MESH-bejelentkezés szükséges",
        "Se requiere acceso a MESH",
        "Connexion à MESH requise"
    ),
    NoStudentSelected(
        "Не выбран ученик",
        "No student selected",
        "Nincs kiválasztva tanuló",
        "Ningún alumno seleccionado",
        "Aucun élève sélectionné"
    ),
    UnknownMethod(
        "Неизвестный метод: %s",
        "Unknown method: %s",
        "Ismeretlen metódus: %s",
        "Método desconocido: %s",
        "Méthode inconnue : %s"
    );

    val text: String
        get() = when (UiLanguage.code) {
            "en" -> en
            "hu" -> hu
            "es" -> es
            "fr" -> fr
            else -> ru
        }

    operator fun invoke(vararg values: Any?): String =
        String.format(UiLanguage.locale, text, *values)
}
