# MySchool OCR

Application Android (Kotlin + Jetpack Compose) permettant à un professeur de gérer ses
grilles de cotation et de les remplir automatiquement en scannant les copies corrigées.
Tout fonctionne en local (base Room/SQLite), aucune donnée n'est envoyée sur un serveur.

## Écrans

Navigation par barre du bas à 3 onglets :

- **Accueil** — statistiques (nombre de grilles, copies corrigées, moyenne générale) +
  liste des grilles de cotation.
- **Classes** — listes d'élèves réutilisables (ex: "6ème A"), importables directement
  dans une nouvelle grille sans les retaper/rescanner à chaque évaluation.
- **Réglages** — langue de reconnaissance OCR (français/anglais) et seuil de confiance
  pour la correspondance automatique du nom.

Depuis une grille : création/édition (nom, matière, colonnes de notation, élèves),
scan d'une liste de classe papier, détail en vue tableau, scan des copies.

## Structure d'une grille de cotation

Calquée sur le document papier de référence : une grille a plusieurs **colonnes de
notation** (ex: Interrogation /20, Devoir /20, Examen /60 — modifiables), un **Total**
calculé automatiquement (somme des colonnes) et une **Mention** calculée automatiquement :

- TB (Très Bien) ≥ 80% · B (Bien) 60–79% · AB (Assez Bien) 50–59%
- P (Passable) 40–49% · I (Insuffisant) < 40%

Le scan des copies se fait **colonne par colonne** (le prof choisit en haut de l'écran
quelle colonne il est en train de corriger) plutôt que de tenter de détecter toutes les
colonnes d'un coup sur une seule photo — plus fiable vu que le format des copies varie.

La colonne "Signature" du document papier n'est pas gérée numériquement (une signature
manuscrite n'a pas de sens à stocker) — à voir si besoin d'un équivalent (ex: case "signé").

## Fonctionnement offline

- Le stockage (grilles, colonnes, élèves, notes, classes, réglages) est 100% local
  (Room + DataStore).
- L'OCR utilise **Tesseract4Android**. Les fichiers de données linguistiques (français
  et anglais) sont empaquetés dans l'app au moment du build (téléchargés automatiquement
  par le workflow GitHub Actions). L'app est donc **100% offline dès le premier lancement**.
- ⚠️ Vérifie la version de `cz.adaptech.tesseract4android:tesseract4android` dans
  `app/build.gradle.kts` au moment du build (fixée à `4.6.0` de mémoire, sans pouvoir
  vérifier en direct la dernière version sur Maven Central).

## En-tête complet + export PDF (façon document papier)

Suite à un modèle de référence fourni par l'utilisateur, la grille inclut maintenant :
- **Champs d'en-tête** : Classe, Professeur, Date, Trimestre (en plus du nom et de la
  matière déjà existants), éditables dans l'écran de création/édition, affichés en haut
  du détail de la grille.
- **Colonne N°** dans le tableau (numéro de ligne).
- **Mention en pastille colorée** (TB=vert, B=bleu, AB=violet, P=ambre, I=rouge) au lieu
  d'un simple texte.
- **Légende du barème des mentions** affichée sous le tableau.
- **Export PDF** (`pdf/GridPdfExporter.kt`) en plus du CSV existant, via un menu (icône
  ⋮ dans la barre du haut) : génère un document avec en-tête, tableau numéroté, pastilles
  de couleur et ligne de signature — dans le même esprit visuel que le document papier de
  référence. Utilise l'API `PdfDocument` native d'Android (aucune dépendance ajoutée,
  fonctionne hors-ligne), avec pagination automatique si la liste d'élèves est longue.



En plus de l'estimation par position du texte (`GridTableParser.parse`), l'app tente
d'abord une **vraie détection du quadrillage papier** via OpenCV :

1. `TableGridDetector.kt` isole les lignes horizontales et verticales du tableau
   (érosion/dilatation directionnelle), puis localise leurs positions exactes par profil
   de projection — ça donne le **nombre réel de lignes et de colonnes**.
2. Chaque cellule ainsi délimitée est découpée et passée individuellement à l'OCR
   (`OcrAnalyzer.recognizeCellText`, mode "ligne unique") — ça lit **ce qui est écrit dans
   chaque case**, y compris les notes déjà remplies sur le papier.
3. `GridTableParser.fromCellGrid` transforme cette grille de cellules en colonnes +
   élèves + notes déjà écrites (`ParsedScore`), avec correspondance par nom (pas par
   position) au moment d'appliquer les notes après création — robuste si le prof corrige
   un nom avant d'enregistrer.
4. Si aucun quadrillage exploitable n'est détecté (photo sans lignes visibles, tableau
   trop inclus...), repli automatique sur l'estimation par position du texte (sans les
   notes, qui nécessitent la détection par cellule).

## Corrections suite au premier vrai build (merci pour les logs !)

Le premier build réel a révélé plusieurs erreurs que je n'avais pas pu voir sans compiler :
- `PageIteratorLevel` n'existe pas comme classe séparée dans cette version de
  tesseract4android → remplacé par les constantes entières `RIL_TEXTLINE`/`RIL_WORD`
  directement (valeurs officielles de l'API Tesseract).
- Import manquant (`getValue`/`setValue`) pour les délégués `by` dans `PremiumComponents.kt`.
- Signature de transition de navigation incorrecte dans `AppNav.kt`
  (`AnimatedContentScope.(NavBackStackEntry) -> Unit` attendu, pas juste `(NavBackStackEntry) -> Unit`).
- `HomeScreen.kt` référençait encore `totalStudentsScanned`, renommé en
  `totalStudentsWithAtLeastOneScore` lors d'une refonte précédente.
- `Font()` appelée avec les arguments dans le mauvais ordre dans `AppFonts.kt`
  (`Font(path, assetManager, ...)`, pas `Font(assetManager, path, ...)`).



Depuis l'écran de création d'une grille, le bouton **"Scanner une grille papier
complète"** permet de photographier un tableau déjà existant (comme le modèle avec
colonnes Interrogation/Devoir/Examen) : l'app reconstitue les colonnes de notation et la
liste des élèves, puis atterrit directement dans l'écran d'édition, déjà pré-rempli mais
entièrement modifiable — rien n'est enregistré tant que tu n'as pas validé.

- OCR au niveau du mot (`OcrAnalyzer.recognizeWords`), pas de la ligne entière, pour
  connaître la position horizontale de chaque mot.
- `ocr/GridTableParser.kt` reconstitue les lignes (regroupement par position verticale)
  puis les colonnes (position horizontale), repère l'en-tête via les mots "Nom"/"Élève",
  extrait le barème d'une colonne depuis un texte du type "Interrogation /20".
- **Limite assumée** : c'est une heuristique basée sur la position du texte, pas une
  vraie détection des traits du tableau (ça reste la plus grosse pièce du pipeline OpenCV
  d'origine, volontairement laissée de côté). Fonctionne mieux sur une photo bien à plat,
  cadrée sur tout le tableau, avec un bon éclairage. Les notes déjà écrites sur le papier
  ne sont pas préremplies dans cette version — seule la structure (colonnes + élèves) est
  reconstituée.
- Si aucun élève n'est détecté, l'app le signale et propose de reprendre la photo plutôt
  que de créer une grille vide silencieusement.



- **MVVM maintenant sur 5 écrans** : Accueil, Détail de grille, Classes, Réglages,
  Vérification (`ClassesViewModel`, `SettingsViewModel`, `VerificationViewModel` ajoutés).
  Toujours en accès direct au Repository : édition de grille/classe, scan de copies, scan
  de liste papier — volontairement, car ce sont les écrans avec caméra/formulaires
  complexes où le risque de régression sans pouvoir compiler était le plus élevé.
- **Barre de statut système thémée** : suit maintenant le clair/sombre choisi dans
  Réglages (icônes claires sur fond sombre, sombres sur fond clair), via `MainActivity`.
- **Cartes premium partout** : Réglages est maintenant en sections `PremiumCard`, les
  lignes de classes/grilles ont icône + carte avec ombre douce, le tableau du détail de
  grille est enveloppé dans une carte plutôt que flottant nu sur le fond.
- **États vides/chargement uniformisés** : `EmptyState` et `LoadingScreen` utilisés sur
  Accueil, Classes, Vérification, et les écrans d'édition (au lieu d'un `Text` ou
  `CircularProgressIndicator` nu).
- Boutons principaux (créer/enregistrer une grille ou une classe) arrondis pour rester
  visuellement cohérents avec le reste.



- **Roboto** (`FontFamily.Default`, police système Android — aucun fichier à charger) :
  tout le corps de texte, tableaux, noms d'élèves, résultats OCR. Choix délibéré pour la
  lisibilité sur de la donnée dense.
- **Inter** : titres, boutons, labels d'interface (chargée depuis les assets, voir
  `ui/theme/AppFonts.kt`).
- **Poppins** : utilisée avec parcimonie sur seulement 2 éléments de marque (titre
  "MySchool OCR" dans la barre du haut de l'Accueil, titre du message de bienvenue) via
  le composant `BrandTitle` — jamais pour de la donnée ou du texte long.

Comme pour Tesseract/OpenCV, les fichiers de police (Inter/Poppins) sont téléchargés par
le workflow GitHub Actions dans `assets/fonts/`, PAS en ressources `res/font/` : si le
téléchargement échoue (chemin Google Fonts changé, etc.), l'app compile et fonctionne
quand même, avec un repli silencieux sur Roboto partout plutôt qu'un échec de build ou un
crash. Cette étape est explicitly non-bloquante (`continue-on-error: true`) dans le
workflow. ⚠️ Comme pour les autres intégrations, le chemin exact d'Inter dans le dépôt
Google Fonts (`ofl/inter/static/...`) est à vérifier au premier run si les polices
n'apparaissent pas — Poppins est un format plus stable historiquement, moins à risque.



- **Système de design** (`ui/theme/`) : palette de couleurs complète Material 3 (clair +
  sombre), typographie avec hiérarchie marquée, coins arrondis généreux. Comme tous les
  écrans utilisent déjà `MaterialTheme.colorScheme`/`typography`, ce changement améliore
  visuellement **toute l'app d'un coup**, sans avoir dû toucher chaque écran.
- **Composants premium réutilisables** (`ui/components/PremiumComponents.kt`) :
  `PremiumButton` (léger effet d'enfoncement animé au clic), `PremiumCard` (ombre douce),
  `LoadingScreen` (écran de chargement animé), `EmptyState` (état vide illustré).
- **Transitions animées** entre tous les écrans (`ui/navigation/AppNav.kt`) : glissement +
  fondu, cohérent dans toute la navigation.
- **Accueil repensé** : carte de statistiques en dégradé, cartes de grilles avec icône,
  état vide illustré.
- **Architecture MVVM appliquée concrètement** sur les 2 écrans les plus visibles :
  - `ui/viewmodel/HomeViewModel.kt` — combine grilles + stats + recherche en un seul état.
  - `ui/viewmodel/GridDetailViewModel.kt` — combine grille + colonnes + tableau + scans en
    attente, expose les actions (note, recalcul de formule, suppression) sans que l'écran
    ait à gérer de coroutines lui-même.
  - `ui/viewmodel/GenericViewModelFactory.kt` — factory minimaliste pour injecter le
    Repository dans un ViewModel sans ajouter Hilt/Koin.

**Volontairement pas fait dans cette session** : les 7 autres écrans (édition de
grille/classe, scan de copies, scan de liste, vérification, réglages, liste des classes)
continuent d'appeler le Repository directement — les convertir en MVVM représente autant
de fichiers que ce qui a déjà été fait, avec un risque de casse proportionnel sans pouvoir
compiler pour vérifier à chaque étape. La conversion suit exactement le même patron
(ViewModel + `StateFlow<UiState>` + `GenericViewModelFactory`) si tu veux qu'on continue.



- **Suppression d'une grille** : icône poubelle dans l'écran de détail, avec confirmation.
- **Navigation** : flèche retour explicite sur tous les écrans secondaires (détail, édition
  de grille/classe, vérification), en plus du geste/bouton système.
- **Notes non confirmées** : un petit point orange dans le tableau signale une note posée
  automatiquement par l'OCR pendant le scan en rafale (jamais relue par le prof).
- **Écrans scrollables** : les écrans avec beaucoup de champs (édition de grille/classe,
  réglages) utilisent maintenant un seul conteneur scrollable (LazyColumn unique ou
  verticalScroll), au lieu d'une zone fixe qui pouvait couper du contenu.
- **Recherche** : champ de recherche par nom sur la liste des grilles (Accueil).
- **Message de bienvenue** une seule fois au premier lancement, expliquant les 3 onglets.
- **Sauvegarde** : bouton "Exporter toutes les données" dans Réglages → fichier JSON complet
  (grilles, colonnes, élèves, notes, classes, sans les photos), partageable. La
  ré-importation automatique n'est pas implémentée (fichier de secours, pas un système de
  restauration en un clic).
- **Signature release** : `app/build.gradle.kts` lit un keystore via des variables
  d'environnement (jamais en dur). Le workflow GitHub Actions build un APK release signé
  automatiquement SI les secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` sont configurés (Settings du repo → Secrets
  and variables → Actions). Sans ces secrets, seul l'APK debug est produit, comme avant.
- **Tests unitaires** : `FormulaEvaluatorTest.kt` et `GradeMentionsTest.kt` (logique pure,
  sans dépendance Android — tournent directement sur JVM). Exécutés automatiquement dans
  le workflow (`gradle test`) avant même de builder l'APK.

### Volontairement pas fait (pour rester réaliste)

- **Migrations Room formelles** : le schéma a beaucoup changé pendant le développement et
  aucune vraie base utilisateur n'existe encore à préserver ; `fallbackToDestructiveMigration()`
  reste en place. À remplacer par de vraies `Migration` avant une mise en production réelle.
- **Icône adaptative** (Android 8+) : l'icône actuelle est un vecteur simple qui s'affiche
  correctement partout, juste sans les effets de masque/parallaxe des icônes adaptatives —
  purement cosmétique, pas prioritaire.
- **Détection réelle des lignes/cellules du tableau papier** (mapper une case à sa colonne
  exacte via la structure géométrique) : la partie la plus complexe du pipeline OpenCV
  d'origine, laissée de côté pour l'instant — l'app se base sur position/taille/majuscules
  du texte plutôt que sur la géométrie du tableau.
- **Ré-importation d'une sauvegarde** : l'export existe, pas l'import.



Avant l'OCR, chaque photo passe par OpenCV (`ocr/ImagePreprocessor.kt`) :

1. **Redressement (deskew)** : détecte l'angle dominant des lignes du tableau/texte
   (Canny + Hough Line Transform) et corrige la rotation si la photo est prise de travers.
   La couleur est conservée à cette étape.
2. **Amélioration pour l'OCR** : niveaux de gris + seuillage adaptatif, utilisé uniquement
   comme entrée de Tesseract (jamais pour l'analyse de couleur, qui a besoin de la couleur
   d'origine).
3. **Détection de couleur (HSV)** : remplace l'ancienne version pixel par pixel en Kotlin
   pur pour repérer l'encre de couleur (rouge, bleu, vert...) sous chaque ligne détectée.

Si OpenCV échoue à s'initialiser sur l'appareil (rare), l'app continue de fonctionner
sans prétraitement : le deskew/contraste est simplement sauté, et la détection de couleur
retombe automatiquement sur l'ancienne méthode Kotlin pure (`coloredInkRatioFallback`
dans `GradeParser.kt`) plutôt que de planter.

⚠️ **Point à vérifier au premier build**, comme pour Tesseract4Android :
- La version `org.opencv:opencv:4.9.0` dans `app/build.gradle.kts` est à confirmer sur
  https://mvnrepository.com/artifact/org.opencv/opencv (coordonnées Maven Central
  publiées par OpenCV directement depuis la 4.9.x environ, à ajuster si besoin).
- `OpenCVLoader.initDebug()` dans `ImagePreprocessor.init()` est la méthode
  d'initialisation la plus universellement présente dans les versions récentes d'OpenCV
  Android (dépréciée mais fonctionnelle). Si une version plus récente la retire, remplace
  par `OpenCVLoader.initLocal()`.



Chaque colonne d'une grille porte une lettre (A, B, C...) selon son ordre, affichée
au-dessus du tableau — comme sur le document papier. Un appui long (2s) sur la lettre
d'une colonne, dans l'écran d'édition de la grille, ouvre un clavier dédié (26 lettres,
chiffres 0-9, les 4 opérations, parenthèses) pour définir une formule de calcul
automatique, par exemple `D = A+B-C/10`. Le bouton "Calculer" valide la syntaxe et
déclenche le calcul pour tous les élèves à partir des colonnes qu'elle référence.

- Les lettres grisées dans le clavier correspondent à des colonnes qui n'existent pas
  (impossible de les insérer dans la formule).
- Un élève à qui il manque une des colonnes référencées est simplement laissé de côté
  (pas de valeur devinée).
- Une colonne "formule" peut être recalculée à tout moment via l'icône calculatrice dans
  l'en-tête du tableau (utile si les colonnes qu'elle référence ont été mises à jour).
- Le Total/Mention automatiques de l'app (les 2 dernières colonnes du tableau) ne
  comptent que les colonnes normales (sans formule), pour éviter de compter deux fois
  une éventuelle colonne "Total" que le prof aurait lui-même définie par formule.



Le scan ne bloque plus après chaque photo. Le prof capture une copie, l'app enchaîne
immédiatement sur la suivante pendant que l'OCR travaille en arrière-plan :

- **Identification fiable** (un seul élève au-dessus du seuil de confiance + une note
  détectée) → la note est enregistrée directement, **aucune photo n'est conservée**.
- **Cas ambigu ou incomplet** (aucun nom fiable, plusieurs élèves possibles, ou aucune
  note détectée) → la copie part dans **Vérification** : sa photo est conservée sur le
  disque (uniquement dans ce cas, pour économiser du stockage) le temps que le prof la
  résolve à la main (choix de l'élève + saisie de la note, avec la meilleure suggestion
  trouvée pré-remplie). Une fois résolue ou ignorée, la photo est supprimée.

Un badge sur l'écran de scan et sur le détail de la grille indique combien de copies
attendent une vérification. Comme les notes appliquées automatiquement ne sont pas
confirmées manuellement par le prof, elles restent marquées en interne (`ocrUnconfirmed`)
pour distinguer plus tard les notes saisies/vérifiées à la main de celles posées par l'OCR.



- **Nom** : comparaison textuelle tolérante aux fautes d'OCR + bonus si la ligne est dans
  le tiers supérieur de la copie + bonus si elle est en majuscules. Le nom n'est
  pré-rempli automatiquement que si **un seul** élève de la grille dépasse le seuil de
  confiance réglé dans Paramètres (75% par défaut). Ambiguïté ou score insuffisant →
  choix manuel.
- **Note** : détection combinée de l'encre de couleur (analyse de la saturation des pixels
  sous chaque ligne — fonctionne au stylo rouge, bleu, vert..., pas seulement rouge) et de
  la taille du texte, en plus des patterns classiques ("/20", "note:").

## Mettre en ligne ce projet sans PC (via navigateur GitHub)

1. Crée un nouveau repository GitHub (vide, sans README).
2. Uploade l'ensemble des fichiers/dossiers de ce projet (en conservant l'arborescence)
   via "Add file" → "Upload files", ou fichier par fichier si besoin sur mobile.
3. Commit sur `main`.
4. Onglet "Actions" → le workflow "Build APK" se déclenche automatiquement.
5. Une fois terminé (icône verte) → section "Artifacts" → télécharge
   `myschool-ocr-debug-apk`. Autorise "Sources inconnues" pour l'installer.

## Structure du projet

```
app/src/main/java/com/myschoolocr/app/
├── MainActivity.kt / MyApp.kt
├── data/
│   ├── Entities.kt                    # GridEntity, StudentEntity
│   ├── GridColumnAndScoreEntities.kt  # GridColumnEntity, StudentScoreEntity
│   ├── ClassEntities.kt               # ClassEntity, ClassStudentEntity
│   ├── Daos.kt / ColumnScoreDaos.kt / ClassDaos.kt
│   ├── AppDatabase.kt                 # Repository (toute la logique métier)
│   ├── SettingsRepository.kt          # Réglages persistés (DataStore)
│   ├── GradeMentions.kt               # Calcul TB/B/AB/P/I
│   └── Editable*Row.kt                # Modèles d'édition (élèves/colonnes)
├── ocr/
│   ├── OcrAnalyzer.kt                 # Tesseract4Android (multi-langue)
│   └── GradeParser.kt                 # Matching nom/note (position, couleur, taille)
└── ui/
    ├── navigation/AppNav.kt           # Navigation + barre du bas
    └── screens/
        ├── HomeScreen.kt              # Accueil (stats + liste des grilles)
        ├── ClassesScreen.kt / ClassEditorScreen.kt
        ├── SettingsScreen.kt
        ├── GridEditorScreen.kt        # Création/édition grille (colonnes + élèves)
        ├── GridScanScreen.kt          # Scan d'une liste de classe papier
        ├── GridDetailScreen.kt        # Vue tableau + export CSV
        ├── CopyScanScreen.kt          # Scan des copies (colonne par colonne)
        └── CameraCaptureScreen.kt
```

## Limites connues / pistes d'amélioration

- Le matching nom/note est heuristique. Le prof valide toujours avant enregistrement —
  volontaire pour éviter les erreurs silencieuses.
- Base de données en version 3, sans migration formelle (`fallbackToDestructiveMigration`) :
  projet en développement actif, le schéma peut encore changer. Si tu as déjà installé
  une version antérieure de l'app, les données seront réinitialisées au prochain schéma.
- Signature de l'APK release non configurée (le workflow ne produit qu'un APK debug).
