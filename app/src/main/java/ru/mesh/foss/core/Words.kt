package ru.mesh.foss.core

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
    Nebula(
        "Туманность",
        "Nebula",
        "Nebula",
        "Nebula",
        "Nébuleuse"
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
    Design(
        "Дизайн",
        "Design",
        "Dizájn",
        "Diseño",
        "Design"
    ),
    DesignPlain(
        "M3E (стандартный)",
        "M3E (standard)",
        "M3E (szabványos)",
        "M3E (estándar)",
        "M3E (standard)"
    ),
    DesignPlainHint(
        "Material 3 Expressive; в альбомной ориентации панель переезжает влево",
        "Material 3 Expressive; the navigation bar moves to the left in landscape",
        "Material 3 Expressive; fekvő tájolásban a navigációs sáv balra kerül",
        "Material 3 Expressive; en horizontal, la barra de navegación pasa a la izquierda",
        "Material 3 Expressive ; en mode paysage, la barre de navigation passe à gauche"
    ),
    DesignLiquid(
        "Жидкое стекло",
        "Liquid glass",
        "Folyékony üveg",
        "Vidrio líquido",
        "Verre liquide"
    ),
    DesignLiquidHint(
        "Оптическое стекло и живая линза в панели навигации (может подтормаживать)",
        "Optical glass panels with a live lens navigation bar (may lag on some devices)",
        "Optikai üveg és élő lencse a navigációs sávon (lassulást okozhat)",
        "Cristal óptico con barra de navegación de lente en vivo (puede ir lento)",
        "Verre optique avec barre de navigation à lentille (peut ralentir l’appareil)"
    ),
    DesignLiquidLagWarning(
        "Жидкое стекло использует живой рендеринг преломления в панели навигации и анимациях и может подтормаживать на некоторых устройствах.",
        "Liquid glass uses live refraction in the navigation bar and animations and may lag on some devices.",
        "A folyékony üveg élő fénytörést használ a navigációs sávon és az animációknál, ezért egyes eszközökön szaggathat.",
        "El vidrio líquido usa refracción en vivo en la barra de navegación y animaciones, por lo que puede ir lento en algunos dispositivos.",
        "Le verre liquide utilise une réfraction en direct dans la barre de navigation et les animations, ce qui peut ralentir certains appareils."
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
    FileNumber(
        "Файл %d",
        "File %d",
        "%d. fájl",
        "Archivo %d",
        "Fichier %d"
    ),
    MarkFiles(
        "Файлы к оценке",
        "Grade files",
        "Értékeléshez csatolt fájlok",
        "Archivos de la nota",
        "Fichiers de la note"
    ),
    StudentWorkFiles(
        "Работа ученика",
        "Student work",
        "Tanulói munka",
        "Trabajo del alumno",
        "Travail de l’élève"
    ),
    CouldNotOpenFile(
        "Не удалось открыть файл",
        "Could not open file",
        "A fájl nem nyitható meg",
        "No se pudo abrir el archivo",
        "Impossible d’ouvrir le fichier"
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
        "Вход по QR-ссылке",
        "Sign in with a QR link",
        "Belépés QR-hivatkozással",
        "Acceder con enlace QR",
        "Connexion par lien QR"
    ),
    QrSignInDescription(
        "МЭШ создаст одноразовую ссылку и откроет её в браузере. Войдите там и вернитесь в приложение — оно завершит вход само.",
        "MESH creates a one-time link and opens it in your browser. Sign in there and return to the app; it will finish signing in automatically.",
        "A MESH egyszeri hivatkozást készít, és megnyitja a böngészőben. Jelentkezzen be ott, majd térjen vissza az alkalmazásba — a belépés automatikusan befejeződik.",
        "MESH creará un enlace de un solo uso y lo abrirá en el navegador. Inicia sesión allí y vuelve a la aplicación; el acceso terminará automáticamente.",
        "MESH crée un lien à usage unique et l’ouvre dans le navigateur. Connectez-vous-y puis revenez dans l’application ; la connexion se terminera automatiquement."
    ),
    SignInInBrowserAndReturn(
        "Войдите через браузер и вернитесь в приложение — вход завершится автоматически.",
        "Sign in through the browser and return to the app; sign-in will finish automatically.",
        "Jelentkezzen be a böngészőben, majd térjen vissza az alkalmazásba — a belépés automatikusan befejeződik.",
        "Inicia sesión en el navegador y vuelve a la aplicación; el acceso terminará automáticamente.",
        "Connectez-vous dans le navigateur puis revenez dans l’application ; la connexion se terminera automatiquement."
    ),
    QrBrowserOpensIn(
        "Браузер откроется через %d с…",
        "The browser will open in %d s…",
        "A böngésző %d másodperc múlva nyílik meg…",
        "El navegador se abrirá en %d s…",
        "Le navigateur s’ouvrira dans %d s…"
    ),
    BigQrSignInButton(
        "Войти по QR-ссылке",
        "Sign in with a QR link",
        "Belépés QR-hivatkozással",
        "Acceder con enlace QR",
        "Se connecter par lien QR"
    ),
    ExperimentalSignInMethods(
        "Нестабильные способы входа",
        "Unstable sign-in methods",
        "Instabil belépési módok",
        "Métodos de acceso inestables",
        "Méthodes de connexion instables"
    ),
    ExperimentalSignInWarning(
        "Экспериментальные способы. Используйте их, только если вход по QR-ссылке не работает.",
        "Experimental methods. Use them only if QR-link sign-in does not work.",
        "Kísérleti módok. Csak akkor használja őket, ha a QR-hivatkozásos belépés nem működik.",
        "Métodos experimentales. Úsalos solo si el acceso con enlace QR no funciona.",
        "Méthodes expérimentales. Utilisez-les uniquement si la connexion par lien QR ne fonctionne pas."
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
    CdzSelfWork(
        "Самостоятельная работа",
        "Independent work",
        "Önálló munka",
        "Trabajo independiente",
        "Travail autonome"
    ),
    CdzTask(
        "Задание",
        "Task",
        "Feladat",
        "Tarea",
        "Tâche"
    ),
    CdzThematicTest(
        "Тематический тест",
        "Topic test",
        "Témazáró teszt",
        "Prueba temática",
        "Test thématique"
    ),
    CdzNotCompleted(
        "Не выполнено",
        "Not completed",
        "Nincs teljesítve",
        "No completado",
        "Non terminé"
    ),
    CdzInProgress(
        "Начато, не завершено",
        "Started, not finished",
        "Elkezdve, nincs befejezve",
        "Iniciado, sin terminar",
        "Commencé, non terminé"
    ),
    CdzCompleted(
        "Выполнено",
        "Completed",
        "Teljesítve",
        "Completado",
        "Terminé"
    ),
    CdzCompletedAt(
        "Выполнено %1\$s в %2\$s",
        "Completed %1\$s at %2\$s",
        "Teljesítve: %1\$s, %2\$s",
        "Completado el %1\$s a las %2\$s",
        "Terminé le %1\$s à %2\$s"
    ),
    CdzInDuration(
        "за %s",
        "in %s",
        "%s alatt",
        "en %s",
        "en %s"
    ),
    CdzScore(
        "%1\$s/%2\$s баллов",
        "%1\$s/%2\$s points",
        "%1\$s/%2\$s pont",
        "%1\$s/%2\$s puntos",
        "%1\$s/%2\$s points"
    ),
    CdzScoreOnly(
        "%s баллов",
        "%s points",
        "%s pont",
        "%s puntos",
        "%s points"
    ),
    CdzMark(
        "Оценка: %s",
        "Mark: %s",
        "Jegy: %s",
        "Nota: %s",
        "Note : %s"
    ),
    CdzAttempts(
        "Попыток: %d",
        "Attempts: %d",
        "Próbálkozások: %d",
        "Intentos: %d",
        "Tentatives : %d"
    ),
    CdzResult(
        "Результат",
        "Result",
        "Eredmény",
        "Resultado",
        "Résultat"
    ),
    CdzStart(
        "Открыть",
        "Open",
        "Megnyitás",
        "Abrir",
        "Ouvrir"
    ),
    CdzStatusLoading(
        "Проверяем выполнение…",
        "Checking completion…",
        "Teljesítés ellenőrzése…",
        "Comprobando finalización…",
        "Vérification de l’état…"
    ),
    CdzStatusFailed(
        "Статус недоступен",
        "Status unavailable",
        "Állapot nem elérhető",
        "Estado no disponible",
        "Statut indisponible"
    ),
    CdzProgress(
        "ЦДЗ: %1\$d из %2\$d выполнено",
        "Digital homework: %1\$d of %2\$d done",
        "Digitális házi: %1\$d / %2\$d kész",
        "Tareas digitales: %1\$d de %2\$d completadas",
        "Devoirs numériques : %1\$d sur %2\$d effectués"
    ),
    CdzTimerTitle(
        "Начать работу?",
        "Start the work?",
        "Elkezdi a feladatot?",
        "¿Empezar la tarea?",
        "Commencer le travail ?"
    ),
    CdzTimerWarning(
        "Материал «Облака Знаний» запускает таймер сразу после открытия. Ссылка одноразовая: чтобы вернуться к работе, откройте её снова из приложения.",
        "This Oblako Znaniy material starts its timer as soon as it opens. The link is single-use: to return to the work, open it again from the app.",
        "Az Oblako Znaniy tananyaga megnyitáskor azonnal elindítja az időzítőt. A link egyszer használatos: a folytatáshoz nyissa meg újra az alkalmazásból.",
        "Este material de Oblako Znaniy inicia su temporizador nada más abrirse. El enlace es de un solo uso: para volver al trabajo, ábrelo de nuevo desde la aplicación.",
        "Ce contenu d’Oblako Znaniy lance son minuteur dès l’ouverture. Le lien est à usage unique : pour revenir au travail, ouvrez-le à nouveau depuis l’application."
    ),
    CdzBegin(
        "Начать",
        "Begin",
        "Kezdés",
        "Comenzar",
        "Démarrer"
    ),
    CdzWindowTitle(
        "ЦДЗ",
        "Digital homework",
        "Digitális házi",
        "Tareas digitales",
        "Devoirs numériques"
    ),
    CdzOpenInBrowser(
        "Браузер",
        "Browser",
        "Böngésző",
        "Navegador",
        "Navigateur"
    ),
    CdzBrowser(
        "Браузер",
        "Browser",
        "Böngésző",
        "Navegador",
        "Navigateur"
    ),
    CdzWatch(
        "Смотреть",
        "Watch",
        "Megnézés",
        "Ver",
        "Regarder"
    ),
    CdzOpenInApp(
        "Нативное решение ЦДЗ",
        "Native digital homework",
        "Natív digitális házi",
        "Resolución nativa de tareas digitales",
        "Résolution native des devoirs numériques"
    ),
    CdzOpenInAppHint(
        "Решать ЦДЗ в приложении: сохранение вручную и предупреждение перед завершением. Включено по умолчанию; можно открывать в веб-странице.",
        "Solve digital homework in the app with manual saving and a completion warning. On by default; you can switch to the web page.",
        "Digitális házi megoldása az appban kézi mentéssel és figyelmeztetéssel. Alapértelmezés szerint be van kapcsolva; átválthat a weboldalra.",
        "Resolver tareas digitales en la app con guardado manual y aviso antes de terminar. Activado por defecto; puede cambiar a la página web.",
        "Résoudre les devoirs numériques dans l’app avec enregistrement manuel et avertissement. Activé par défaut ; vous pouvez passer à la page Web."
    ),
    CdzReusedLink(
        "Ссылка запуска одноразовая — получаем новую…",
        "The launch link is single-use — getting a new one…",
        "Az indítólink egyszer használatos — új lekérése…",
        "El enlace de inicio es de un solo uso — obteniendo uno nuevo…",
        "Le lien de lancement est à usage unique — obtention d’un nouveau…"
    ),
    DurationHours(
        "%d ч",
        "%d h",
        "%d ó",
        "%d h",
        "%d h"
    ),
    DurationMinutes(
        "%d м",
        "%d min",
        "%d p",
        "%d min",
        "%d min"
    ),
    DurationSeconds(
        "%d с",
        "%d s",
        "%d mp",
        "%d s",
        "%d s"
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
    ),
    Moskvenok(
        "Москвёнок",
        "Moskvenok",
        "Moskvenok",
        "Moskvenok",
        "Moskvenok"
    ),
    EnableMoskvenok(
        "Включить Москвёнок",
        "Enable Moskvenok",
        "Moskvenok engedélyezése",
        "Activar Moskvenok",
        "Activer Moskvenok"
    ),
    MoskvenokAbout(
        "Карта «Москвёнок» — баланс школьных документов и QR-код для прохода в школу.",
        "The Moskvenok card — school document balance and a QR code for entering the school.",
        "A Moskvenok kártya — iskolai dokumentumegyenleg és QR-kód az iskolába jutáshoz.",
        "La tarjeta Moskvenok: saldo de documentos escolares y un código QR para entrar en el colegio.",
        "La carte Moskvenok : solde des documents scolaires et un code QR pour entrer à l’école."
    ),
    MoskvenokLoading(
        "Загружаем Москвёнок…",
        "Loading Moskvenok…",
        "Moskvenok betöltése…",
        "Cargando Moskvenok…",
        "Chargement de Moskvenok…"
    ),
    MoskvenokProcessing(
        "Идёт обработка документов…",
        "Processing documents…",
        "A dokumentumok feldolgozása…",
        "Procesando documentos…",
        "Traitement des documents…"
    ),
    MoskvenokRetryEnable(
        "Повторить включение",
        "Retry enabling",
        "Engedélyezés újra",
        "Reintentar activación",
        "Réessayer l’activation"
    ),
    MoskvenokQrExpired(
        "Код устарел",
        "Code expired",
        "Kód lejárt",
        "Código caducado",
        "Code expiré"
    ),
    MoskvenokRefreshQr(
        "Обновить QR-код",
        "Refresh QR code",
        "QR-kód frissítése",
        "Actualizar el código QR",
        "Actualiser le code QR"
    ),
    MoskvenokValidFor(
        "Действует ещё %s",
        "Valid for %s more",
        "Még %s érvényes",
        "Válido otros %s",
        "Valable encore %s"
    ),
    MoskvenokNoSession(
        "Сессия «Моя ID» не получена. Войдите через QR или school.mos.ru и откройте экран заново.",
        "The My ID session was not obtained. Sign in via QR or school.mos.ru and reopen this screen.",
        "A „My ID” munkamenet nem szerzett be. Jelentkezzen be QR-n vagy a school.mos.ru oldalon, majd nyissa meg újra ezt a képernyőt.",
        "No se obtuvo la sesión de «My ID». Inicie sesión con el QR o en school.mos.ru y vuelva a abrir esta pantalla.",
        "La session «My ID » n’a pas été obtenue. Connectez-vous via QR ou school.mos.ru, puis rouvrez cet écran."
    ),
    MoskvenokError(
        "Не удалось загрузить Москвёнок. Попробуйте позже.",
        "Could not load Moskvenok. Try again later.",
        "A Moskvenok nem tölthető be. Próbálja újra később.",
        "No se pudo cargar Moskvenok. Inténtelo más tarde.",
        "Impossible de charger Moskvenok. Réessayez plus tard."
    ),
    MoskvenokEnableFailed(
        "Карта не включилась. Обновите экран или повторите попытку.",
        "The card was not enabled. Refresh the screen or try again.",
        "A kártya nem kapcsolt be. Frissítse a képernyőt, vagy próbálja újra.",
        "La tarjeta no se activó. Actualice la pantalla o inténtelo de nuevo.",
        "La carte n’a pas été activée. Actualisez l’écran ou réessayez."
    ),
    MoskvenokLog(
        "Журнал приложения",
        "Application log",
        "Alkalmazásnapló",
        "Registro de la aplicación",
        "Journal de l’application"
    ),
    MoskvenokLogHint(
        "Запросы, ответы, ошибки и события приложения.",
        "Requests, responses, errors and app events.",
        "Kérések, válaszok, hibák és alkalmazásesemények.",
        "Solicitudes, respuestas, errores y eventos de la aplicación.",
        "Requêtes, réponses, erreurs et événements de l’application."
    ),
    ShowMoskvenokLog(
        "Показать лог",
        "Show log",
        "Napló megjelenítése",
        "Ver registro",
        "Afficher le journal"
    ),
    ClearMoskvenokLog(
        "Очистить лог",
        "Clear log",
        "Napló törlése",
        "Borrar registro",
        "Effacer le journal"
    ),
    CopyMoskvenokLog(
        "Копировать лог",
        "Copy log",
        "Napló másolása",
        "Copiar registro",
        "Copier le journal"
    ),
    MoskvenokLogEmpty(
        "Журнал пуст. Включите переключатель и повторите действие.",
        "The log is empty. Turn on the toggle and repeat an action.",
        "A napló üres. Kapcsolja be a kapcsolót, és ismételjen meg egy műveletet.",
        "El registro está vacío. Active el interruptor y repita una acción.",
        "Le journal est vide. Activez l’interrupteur et répétez une action."
    ),
    Attendance(
        "Посещаемость",
        "Attendance",
        "Jelenlét",
        "Asistencia",
        "Présence"
    ),
    Absences(
        "Пропуски",
        "Absences",
        "Hiányzások",
        "Ausencias",
        "Absences"
    ),
    Visits(
        "Проходы",
        "Entries",
        "Belépések",
        "Accesos",
        "Passages"
    ),
    Entry(
        "Вход",
        "Entry",
        "Belépés",
        "Entrada",
        "Entrée"
    ),
    Exit(
        "Выход",
        "Exit",
        "Kilépés",
        "Salida",
        "Sortie"
    ),
    StillInside(
        "ещё в школе",
        "still at school",
        "még az iskolában",
        "aún en la escuela",
        "encore à l’école"
    ),
    NoAttendanceForWeek(
        "За эту неделю нет пропусков",
        "No absences this week",
        "Ezen a héten nincs hiányzás",
        "No hay ausencias esta semana",
        "Aucune absence cette semaine"
    ),
    SelectAStudentToViewAttendance(
        "Выберите ученика, чтобы открыть посещаемость",
        "Select a student to view attendance",
        "Válasszon tanulót a jelenlét megtekintéséhez",
        "Seleccione un alumno para ver la asistencia",
        "Sélectionnez un élève pour voir la présence"
    ),
    More(
        "Ещё",
        "More",
        "Több",
        "Más",
        "Plus"
    ),
    EditTabs(
        "Редактировать",
        "Edit",
        "Szerkesztés",
        "Editar",
        "Modifier"
    ),
    Done(
        "Готово",
        "Done",
        "Kész",
        "Listo",
        "Terminé"
    ),
    OnBar(
        "На панели",
        "On the bar",
        "A sávon",
        "En la barra",
        "Sur la barre"
    ),
    InMore(
        "В «Ещё»",
        "In «More»",
        "A «Továbbiak»",
        "En «Más»",
        "Dans «Plus»"
    ),
    DragHint(
        "Перетащите, чтобы изменить порядок",
        "Drag to reorder",
        "Húzza az átrendezéshez",
        "Arrastre para reordenar",
        "Glissez pour réorganiser"
    ),
    Save(
        "Сохранить",
        "Save",
        "Mentés",
        "Guardar",
        "Enregistrer"
    ),
    Refresh(
        "Обновить",
        "Refresh",
        "Frissítés",
        "Actualizar",
        "Actualiser"
    ),
    Reason(
        "Причина",
        "Reason",
        "Ok",
        "Motivo",
        "Raison"
    ),
    WidgetNoData(
        "Нет данных. Откройте MESHFOSS, чтобы обновить виджет.",
        "No data. Open MESHFOSS to refresh the widget.",
        "Nincs adat. Nyissa meg a MESHFOSS-t a widget frissítéséhez.",
        "Sin datos. Abra MESHFOSS para actualizar el widget.",
        "Aucune donnée. Ouvrez MESHFOSS pour actualiser le widget."
    ),
    WidgetLessons(
        "Уроков: %d",
        "Lessons: %d",
        "Tanórák: %d",
        "Clases: %d",
        "Cours : %d"
    ),
    WidgetHomework(
        "ДЗ: %d",
        "Homework: %d",
        "Házi: %d",
        "Tareas: %d",
        "Devoirs : %d"
    ),
    QrLinkCodeHint(
        "QR-код этой ссылки",
        "QR code for this link",
        "A hivás QR-kódja",
        "Código QR de este enlace",
        "Code QR de ce lien"
    ),
    WidgetAtLesson(
        "Сейчас урок",
        "Lesson now",
        "Óra most",
        "Clase ahora",
        "Cours en cours"
    ),
    WidgetNextLesson(
        "Следующий урок",
        "Next lesson",
        "Következő óra",
        "Siguiente clase",
        "Prochain cours"
    ),
    WidgetLessonsOver(
        "Уроков больше нет",
        "No more lessons",
        "Nincs több óra",
        "No hay más clases",
        "Plus de cours"
    ),
    WidgetUntilEnd(
        "До конца: %s",
        "Until end: %s",
        "Végéig: %s",
        "Hasta el final: %s",
        "Jusqu’à la fin : %s"
    ),
    WidgetUntilStart(
        "До начала: %s",
        "Until start: %s",
        "Kezdetig: %s",
        "Hasta el inicio: %s",
        "Jusqu’au début : %s"
    ),
    WidgetNextAt(
        "Далее в %s — %s",
        "Next at %s — %s",
        "Következő %s — %s",
        "Siguiente a las %s — %s",
        "Suivant à %s — %s"
    ),
    WidgetMoreLessons(
        "И ещё: %d",
        "And %d more",
        "És még %d",
        "Y %d más",
        "Et %d de plus"
    ),
    WidgetNoHomework(
        "Невыполненного ДЗ нет",
        "No pending homework",
        "Nincs elvégzetlen házi",
        "No hay tareas pendientes",
        "Aucun devoir en attente"
    ),
    WidgetMoreHomework(
        "И ещё: %d",
        "And %d more",
        "És még %d",
        "Y %d más",
        "Et %d de plus"
    ),
    Accounts(
        "Аккаунты",
        "Accounts",
        "Fiókok",
        "Cuentas",
        "Comptes"
    ),
    AddAccount(
        "Добавить аккаунт",
        "Add account",
        "Fiók hozzáadása",
        "Añadir cuenta",
        "Ajouter un compte"
    ),
    SwitchAccount(
        "Переключиться",
        "Switch to",
        "Váltás",
        "Cambiar a",
        "Basculer vers"
    ),
    RemoveAccount(
        "Удалить аккаунт",
        "Remove account",
        "Fiók törlése",
        "Eliminar cuenta",
        "Supprimer le compte"
    ),
    NoAccountsYet(
        "Аккаунтов пока нет. Войдите по QR-ссылке или по cookie JSON.",
        "No accounts yet. Sign in with a QR link or cookie JSON.",
        "Még nincs fiók. Jelentkezzen be QR-hivatkozással vagy cookie JSON-nal.",
        "Aún no hay cuentas. Inicie sesión con un enlace QR o JSON de cookies.",
        "Aucun compte. Connectez-vous avec un lien QR ou un JSON de cookies."
    ),
    SignInWithCookiesJson(
        "Войти по cookie JSON",
        "Sign in with cookie JSON",
        "Bejelentkezés cookie JSON-nal",
        "Iniciar sesión con JSON de cookies",
        "Se connecter avec un JSON de cookies"
    ),
    CookiesJsonHint(
        "Выберите файл (например mos_cookies.json) или вставьте его содержимое. Куки останутся только на этом устройстве.",
        "Pick a file (for example mos_cookies.json) or paste its contents. Cookies stay on this device only.",
        "Válasszon fájlt (pl. mos_cookies.json), vagy illessze be a tartalmát. A cookie-k csak ezen az eszközön maradnak.",
        "Seleccione un archivo (por ejemplo mos_cookies.json) o pegue su contenido. Las cookies se quedan solo en este dispositivo.",
        "Choisissez un fichier (par exemple mos_cookies.json) ou collez son contenu. Les cookies restent sur cet appareil."
    ),
    PasteCookiesJson(
        "Вставить JSON",
        "Paste JSON",
        "JSON beillesztése",
        "Pegar JSON",
        "Coller le JSON"
    ),
    PickCookiesFile(
        "Выбрать файл",
        "Pick file",
        "Fájl kiválasztása",
        "Elegir archivo",
        "Choisir un fichier"
    ),
    CookiesInvalid(
        "Не удалось прочитать cookie JSON. Нужны поля name и value.",
        "Could not read cookie JSON. Fields name and value are required.",
        "Nem sikerült beolvasni a cookie JSON-t. A name és value mezők szükségesek.",
        "No se pudo leer el JSON de cookies. Se requieren los campos name y value.",
        "Impossible de lire le JSON de cookies. Les champs name et value sont requis."
    ),
    CookiesSignInFailed(
        "Не удалось войти по кукам. Проверьте, что они ещё действуют.",
        "Could not sign in with these cookies. Check that they are still valid.",
        "Nem sikerült bejelentkezni a cookie-kkal. Ellenőrizze, hogy érvényesek-e még.",
        "No se pudo iniciar sesión con las cookies. Compruebe que sigan siendo válidas.",
        "Impossible de se connecter avec ces cookies. Vérifiez qu’ils sont encore valides."
    ),
    CdzAttemptLoading(
        "Проверяем попытки на сервере…",
        "Checking attempts on the server…",
        "Próbálkozások ellenőrzése…",
        "Comprobando intentos…",
        "Vérification des tentatives…"
    ),
    CdzAttemptHint(
        "Выберите, что делать: продолжить начатую попытку или начать новую. История — только завершённые попытки.",
        "Choose: continue an existing attempt or start a new one. History shows completed attempts only.",
        "Válasszon: folytat egy megkezdett próbálkozást, vagy újat indít. A történet csak a befejezetteket mutatja.",
        "Elija: continuar un intento iniciado o iniciar uno nuevo. El historial muestra solo los completados.",
        "Choisissez : continuer une tentative commencée ou en démarrer une nouvelle. L’historique ne montre que les tentatives terminées."
    ),
    CdzAttemptStatus(
        "Начато попыток: %1\$d · текущая: %2\$s",
        "Attempts started: %1\$d · current: %2\$s",
        "Indított próbálkozások: %1\$d · jelenlegi: %2\$s",
        "Intentos iniciados: %1\$d · actual: %2\$s",
        "Tentatives démarrées : %1\$d · actuelle : %2\$s"
    ),
    CdzAttemptTitle(
        "Попытка ЦДЗ",
        "Digital homework attempt",
        "Digitális házi próbálkozás",
        "Intento de tarea digital",
        "Tentative de devoir numérique"
    ),
    CdzStartAttempt(
        "Начать попытку",
        "Start attempt",
        "Próbálkozás indítása",
        "Iniciar intento",
        "Démarrer la tentative"
    ),
    CdzStartNewAttempt(
        "Начать новую попытку",
        "Start a new attempt",
        "Új próbálkozás indítása",
        "Iniciar un nuevo intento",
        "Démarrer une nouvelle tentative"
    ),
    CdzContinueAttempt(
        "Продолжить попытку %1\$s",
        "Continue attempt %1\$s",
        "Próbálkozás folytatása: %1\$s",
        "Continuar intento %1\$s",
        "Continuer la tentative %1\$s"
    ),
    CdzAttemptHistory(
        "История попыток",
        "Attempt history",
        "Próbálkozások története",
        "Historial de intentos",
        "Historique des tentatives"
    ),
    CdzStartAnyway(
        "Начать",
        "Start",
        "Indítás",
        "Iniciar",
        "Démarrer"
    ),
    CdzFirstAttemptWarning(
        "Сервер ещё не видел ни одной попытки. Начать первую?",
        "No attempts yet. Start the first one?",
        "Még nincs próbálkozás. Indítani az elsőt?",
        "Aún no hay intentos. ¿Iniciar el primero?",
        "Aucune tentative. Démarrer la première ?"
    ),
    CdzNewAttemptWarning(
        "Уже начато попыток: %1\$d. Новая попытка не отменит предыдущие. Продолжить существующую — безопаснее.",
        "There are already %1\$d attempts. A new one will not cancel the previous ones. Continuing an existing attempt is safer.",
        "Már %1\$d próbálkozás van. Az új nem szünteti meg az előzőket. A meglévő folytatása biztonságosabb.",
        "Ya hay %1\$d intentos. Uno nuevo no cancela los anteriores. Continuar uno existente es más seguro.",
        "Il existe déjà %1\$d tentatives. Une nouvelle n’annule pas les précédentes. Continuer une tentative existante est plus sûr."
    ),
    CdzNoAttempts(
        "Завершённых попыток пока нет",
        "No completed attempts yet",
        "Még nincs befejezett próbálkozás",
        "Aún no hay intentos completados",
        "Aucune tentative terminée"
    ),
    CdzAttemptRow(
        "№%1\$s · %2\$s · %3\$s/%4\$s",
        "#%1\$s · %2\$s · %3\$s/%4\$s",
        "#%1\$s · %2\$s · %3\$s/%4\$s",
        "№%1\$s · %2\$s · %3\$s/%4\$s",
        "n°%1\$s · %2\$s · %3\$s/%4\$s"
    ),
    CdzNativeTitle(
        "ЦДЗ — нативное решение",
        "Digital homework — native solver",
        "Digitális házi — natív megoldás",
        "Tarea digital — resolución nativa",
        "Devoir numérique — résolution native"
    ),
    CdzNativeError(
        "Не удалось открыть ЦДЗ нативно. Откройте его в браузере.",
        "Could not open the digital homework natively. Open it in the browser.",
        "Nem sikerült natívan megnyitni a digitális házit. Nyissa meg a böngészőben.",
        "No se pudo abrir la tarea digital de forma nativa. Ábrala en el navegador.",
        "Impossible d’ouvrir le devoir numérique nativement. Ouvrez-le dans le navigateur."
    ),
    CdzFinishTitle(
        "Завершить попытку?",
        "Finish the attempt?",
        "Befejezni a próbálkozást?",
        "¿Finalizar el intento?",
        "Terminer la tentative ?"
    ),
    CdzFinishConfirm(
        "Сохранено на сервере %1\$d из %2\$d заданий.",
        "Saved on the server: %1\$d of %2\$d tasks.",
        "A szerveren mentve: %1\$d / %2\$d feladat.",
        "Guardado en el servidor: %1\$d de %2\$d tareas.",
        "Enregistré sur le serveur : %1\$d tâches sur %2\$d."
    ),
    CdzFinishWarning(
        "Решено только %1\$d из %2\$d заданий.",
        "Only %1\$d of %2\$d tasks are solved.",
        "Csak %1\$d / %2\$d feladat van megoldva.",
        "Solo %1\$d de %2\$d tareas están resueltas.",
        "Seules %1\$d tâches sur %2\$d sont résolues."
    ),
    CdzFinishUnsaved(
        "Есть ответы, которые вы не сохраняли. После завершения попытку нельзя продолжить.",
        "There are answers you did not save. The attempt cannot be continued after finishing.",
        "Vannak nem mentett válaszok. A próbálkozás nem folytatható a befejezés után.",
        "Hay respuestas sin guardar. El intento no se puede continuar después de finalizar.",
        "Des réponses ne sont pas enregistrées. La tentative ne pourra pas être reprise."
    ),
    CdzFinishAnyway(
        "Всё равно завершить",
        "Finish anyway",
        "Mégis befejezni",
        "Finalizar de todos modos",
        "Terminer quand même"
    ),
    CdzLeaveTitle(
        "Уйти, не сохранив?",
        "Leave without saving?",
        "Kilépés mentés nélkül?",
        "¿Salir sin guardar?",
        "Quitter sans enregistrer ?"
    ),
    CdzLeaveMessage(
        "Несохранённые ответы останутся только в этом приложении (без отправки на сервер).",
        "Unsaved answers will stay only in this app (not sent to the server).",
        "A nem mentett válaszok csak ebben az alkalmazásban maradnak (nem kerülnek a szerverre).",
        "Las respuestas sin guardar se quedarán solo en la app (no se envían al servidor).",
        "Les réponses non enregistrées resteront dans l’app (non envoyées au serveur)."
    ),
    CdzLeaveAnyway(
        "Уйти",
        "Leave",
        "Kilépés",
        "Salir",
        "Quitter"
    ),
    CdzResultTitle(
        "Попытка завершена",
        "Attempt finished",
        "Próbálkozás befejezve",
        "Intento finalizado",
        "Tentative terminée"
    ),
    CdzResultDone(
        "Ответы отправлены. Результат появится в дневнике.",
        "Answers submitted. The result will appear in the diary.",
        "A válaszok elküldve. Az eredmény a naplóban jelenik meg.",
        "Respuestas enviadas. El resultado aparecerá en el diario.",
        "Réponses envoyées. Le résultat apparaîtra dans le carnet."
    ),
    CdzResultScore(
        "Баллы: %1\$d из %2\$d",
        "Score: %1\$d of %2\$d",
        "Pontszám: %1\$d / %2\$d",
        "Puntuación: %1\$d de %2\$d",
        "Score : %1\$d sur %2\$d"
    ),
    CdzAttemptNumber(
        "Попытка №%1\$d",
        "Attempt #%1\$d",
        "%1\$d. próbálkozás",
        "Intento n.º %1\$d",
        "Tentative n°%1\$d"
    ),
    CdzCorrect(
        "Верно",
        "Correct",
        "Helyes",
        "Correcto",
        "Correct"
    ),
    CdzPartiallyCorrect(
        "Частично верно",
        "Partially correct",
        "Részben helyes",
        "Parcialmente correcto",
        "Partiellement correct"
    ),
    CdzIncorrect(
        "Неверно",
        "Incorrect",
        "Helytelen",
        "Incorrecto",
        "Incorrect"
    ),
    CdzMissed(
        "Пропущено",
        "Missed",
        "Kihagyva",
        "Omitido",
        "Manqué"
    ),
    CdzNoAnswer(
        "Нет ответа",
        "No answer",
        "Nincs válasz",
        "Sin respuesta",
        "Sans réponse"
    ),
    CdzAnswerEmpty(
        "Выберите ответ или введите текст",
        "Choose an answer or enter text",
        "Válasszon választ vagy írjon szöveget",
        "Elija una respuesta o escriba texto",
        "Choisissez une réponse ou saisissez du texte"
    ),
    CdzSaved(
        "Ответ сохранён на сервере",
        "Answer saved on the server",
        "A válasz mentve a szerveren",
        "Respuesta guardada en el servidor",
        "Réponse enregistrée sur le serveur"
    ),
    CdzPrefilled(
        "Ответы из прошлой попытки подставлены. Проверьте и сохраните.",
        "Answers from the previous attempt are filled in. Check and save them.",
        "Az előző próbálkozás válaszai be vannak illesztve. Ellenőrizze és mentse el.",
        "Se han rellenado las respuestas del intento anterior. Compruébalas y guárdalas.",
        "Les réponses de la tentative précédente sont préremplies. Vérifiez-les et enregistrez-les."
    ),
    Option(
        "Вариант",
        "Option",
        "Opció",
        "Opción",
        "Option"
    ),
    YourAnswer(
        "Ваш ответ",
        "Your answer",
        "Az Ön válasza",
        "Su respuesta",
        "Votre réponse"
    ),
    CdzNativeSave(
        "Сохранить",
        "Save",
        "Mentés",
        "Guardar",
        "Enregistrer"
    ),
    CdzNativeFinish(
        "Завершить",
        "Finish",
        "Befejezés",
        "Finalizar",
        "Terminer"
    ),
    CdzPreviousTask(
        "Предыдущее задание",
        "Previous task",
        "Előző feladat",
        "Tarea anterior",
        "Tâche précédente"
    ),
    CdzNextTask(
        "Следующее задание",
        "Next task",
        "Következő feladat",
        "Tarea siguiente",
        "Tâche suivante"
    ),
    Undo(
        "Отменить",
        "Undo",
        "Visszavonás",
        "Deshacer",
        "Annuler"
    ),
    Clear(
        "Очистить",
        "Clear",
        "Törlés",
        "Borrar",
        "Effacer"
    ),
    CdzTaskOf(
        "Задание %1\$d из %2\$d",
        "Task %1\$d of %2\$d",
        "%1\$d. feladat / %2\$d",
        "Tarea %1\$d de %2\$d",
        "Tâche %1\$d sur %2\$d"
    ),
    CdzSavedCount(
        "Сохранено %1\$d из %2\$d",
        "Saved %1\$d of %2\$d",
        "Mentve: %1\$d / %2\$d",
        "Guardadas %1\$d de %2\$d",
        "Enregistré : %1\$d sur %2\$d"
    ),
    CdzUnsavedShort(
        "Есть несохранённые ответы",
        "Some answers are not saved",
        "Vannak nem mentett válaszok",
        "Hay respuestas sin guardar",
        "Des réponses ne sont pas enregistrées"
    ),
    CdzAnswerLabel(
        "Ответ",
        "Answer",
        "Válasz",
        "Respuesta",
        "Réponse"
    ),
    CdzMatchLinked(
        "Связано %1\$d из %2\$d",
        "Matched %1\$d of %2\$d",
        "Összekapcsolva: %1\$d / %2\$d",
        "Emparejados %1\$d de %2\$d",
        "Associés : %1\$d sur %2\$d"
    ),
    CdzMatchHint(
        "Нажмите на два элемента или протяните линию от карточки к карточке. Внутри одной колонки связать нельзя.",
        "Tap two items or drag a line from card to card. Items in the same column cannot be linked.",
        "Koppintson két elemre, vagy húzzon vonalat a kártyák között. Azonos oszlop elemei nem kapcsolhatók össze.",
        "Toque dos elementos o arrastre una línea entre tarjetas. No se pueden unir elementos de la misma columna.",
        "Touchez deux éléments ou faites glisser une ligne entre les cartes. Les éléments d’une même colonne ne peuvent pas être liés."
    ),
    CdzRemoveLink(
        "Удалить связь",
        "Remove link",
        "Kapcsolat törlése",
        "Eliminar enlace",
        "Supprimer le lien"
    ),
    CdzMatchEmpty(
        "В этом задании нет вариантов для сопоставления.",
        "This task has no matching options.",
        "Ehhez a feladathoz nincsenek párosítható elemek.",
        "Esta tarea no tiene opciones para emparejar.",
        "Cette tâche n’a pas d’éléments à associer."
    ),
    CdzGroup(
        "Группа %1\$d",
        "Group %1\$d",
        "%1\$d. csoport",
        "Grupo %1\$d",
        "Groupe %1\$d"
    ),
    CdzCell(
        "Ячейка %1\$d",
        "Cell %1\$d",
        "%1\$d. cella",
        "Celda %1\$d",
        "Cellule %1\$d"
    ),
    CdzUnsupportedType(
        "Тип «%1\$s» пока не поддерживается полностью. Можно ввести ответ текстом.",
        "The “%1\$s” type is not fully supported yet. You can enter the answer as text.",
        "A(z) „%1\$s” típus még nem teljesen támogatott. A válasz szövegként megadható.",
        "El tipo «%1\$s» aún no es compatible por completo. Puede escribir la respuesta.",
        "Le type « %1\$s » n’est pas encore entièrement pris en charge. Vous pouvez saisir la réponse."
    ),
    CdzChoose(
        "Выберите",
        "Choose",
        "Válasszon",
        "Elija",
        "Choisir"
    ),
    CdzImage(
        "Изображение",
        "Image",
        "Kép",
        "Imagen",
        "Image"
    ),
    CdzCorrectAnswer(
        "Правильный ответ",
        "Correct answer",
        "Helyes válasz",
        "Respuesta correcta",
        "Bonne réponse"
    ),
    CdzCorrectAnswerHidden(
        "Сервер не раскрывает правильный ответ для этой попытки.",
        "The server does not reveal the correct answer for this attempt.",
        "A szerver nem fedi fel a helyes választ ehhez a próbálkozáshoz.",
        "El servidor no revela la respuesta correcta de este intento.",
        "Le serveur ne révèle pas la bonne réponse pour cette tentative."
    ),
    CdzAwaitingReview(
        "На проверке",
        "Awaiting review",
        "Ellenőrzésre vár",
        "En revisión",
        "En attente de vérification"
    ),
    CdzAwaitingReviewScore(
        "— / %1\$d",
        "— / %1\$d",
        "— / %1\$d",
        "— / %1\$d",
        "— / %1\$d"
    ),
    CdzAnswerUnavailable(
        "Ответ недоступен",
        "Answer unavailable",
        "A válasz nem érhető el",
        "Respuesta no disponible",
        "Réponse indisponible"
    ),
    CdzVideoUnavailable(
        "Видео недоступно",
        "Video is unavailable",
        "A videó nem érhető el",
        "El vídeo no está disponible",
        "La vidéo n’est pas disponible"
    ),
    CdzVideoSubtitles(
        "Есть субтитры",
        "Subtitles available",
        "Feliratok elérhetők",
        "Hay subtítulos",
        "Sous-titres disponibles"
    ),
    CdzFullscreen(
        "На весь экран",
        "Full screen",
        "Teljes képernyő",
        "Pantalla completa",
        "Plein écran"
    ),
    CdzDownloadStarted(
        "Скачивание в «Загрузки»",
        "Downloading to Downloads",
        "Letöltés a Letöltések mappába",
        "Descargando en Descargas",
        "Téléchargement dans Téléchargements"
    ),
    CdzDownloadFailed(
        "Не удалось начать скачивание",
        "Could not start the download",
        "Nem sikerült elindítani a letöltést",
        "No se pudo iniciar la descarga",
        "Impossible de démarrer le téléchargement"
    ),
    WidgetUpdated(
        "Обновлено %s",
        "Updated %s",
        "Frissítve: %s",
        "Actualizado %s",
        "Actualisé %s"
    ),
    PreviewWithoutSignIn(
        "Предпросмотр без входа",
        "Preview without sign-in",
        "Előnézet bejelentkezés nélkül",
        "Vista previa sin iniciar sesión",
        "Aperçu sans connexion"
    ),
    PreviewMode(
        "Предпросмотр",
        "Preview",
        "Előnézet",
        "Vista previa",
        "Aperçu"
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
