# ZYVIOTV Player — Instructions permanentes pour Claude Code

> Référence du projet depuis sa création. À lire AVANT toute analyse, modification, PR ou fusion.
> État de référence documentaire : 10 octobre 2026, `main` après PR #212 : `394ba49` ; bloc Room en cours (PR #213 = PR A, voir §8).
> Ce document décrit la vision, les décisions immuables, les réalisations observées et les anomalies connues. **Il ne constitue pas une attestation que chaque fonctionnalité est opérationnelle.**

## 0. Règles de travail non négociables

1. Lire d'abord ce document, `README.md`, `docs/ARCHITECTURE.md`, `docs/PLATFORMS.md`, `docs/PHASES.md`, puis inspecter les sources et les workflows actuels. **Le code de `main` prévaut sur toute hypothèse historique** ; documenter les écarts au lieu de les masquer.
2. L'utilisateur échange en **français** ; comptes rendus compréhensibles, courts mais précis, sans jargon gratuit. Il utilise Android Studio sous Windows et teste généralement un Pixel 7 émulé. Fournir des manipulations **concrètes, une action à la fois** quand une intervention manuelle est nécessaire.
3. Agir par **gros blocs fonctionnels cohérents**, avec correctifs, tests et PR ; ne pas faire réexpliquer le contexte connu. Proposer la suite dès que la phase précédente est correctement validée.
4. Ne jamais annoncer « terminé », « fonctionne » ou « testé en réel » sur la seule base de CI, d'un écran visible ou d'un HTTP 200. Séparer **implémenté**, **test automatisé**, **vérifié sur émulateur**, **vérifié sur appareil physique** et **vérifié avec fournisseur réel**.
5. Ne pas fusionner avec des contrôles obligatoires rouges, en attente, des reviews bloquantes ou des discussions non résolues. Vérifier le **SHA précis**, la PR et les workflows après fusion. Ne jamais contourner un quality gate.
6. Préserver les écrans, les composants et les maquettes validées par l'utilisateur. Ne pas refaire le design sans demande explicite ; compléter uniquement les variantes manquantes ou corriger les écarts de fidélité/accessibilité.
7. Ne jamais divulguer, logger, committer ou recopier des identifiants IPTV, URL M3U authentifiées, `username/password`, jetons Supabase, secrets, cookies d'authentification ou autres données personnelles. Expurger également les erreurs, captures et traces de redirection.
8. Ne jamais affaiblir la sécurité pour débloquer un test : pas de désactivation RLS, de permissions globales, d'exposition de tables secrètes, de contournement des protections fournisseur ou d'augmentation arbitraire de heap.
9. Ne pas fusionner, déployer en production ou modifier Supabase en prétendant disposer de validations non réalisées. Vérifier d'abord les capacités et autorisations réellement disponibles.
10. À la fin de chaque phase, actualiser ce document si l'architecture, les contrats, la compatibilité ou les problèmes connus changent, et donner un rapport avec PR, SHA, CI, risques et scénario précis de recette.

## 1. Produit : origine et finalité

**Nom du produit :** ZYVIOTV Player. **Dépôt :** `hkpronosfrance-ops/ZyvioTV-Player`.

Développé à partir d'octobre 2026 comme **lecteur IPTV multiplateforme premium** distinct du site catalogue/marketing ZYVIOTV. Il doit permettre à un utilisateur de se connecter à son compte ZYVIOTV, d'ajouter une ou plusieurs playlists personnelles compatibles **M3U/M3U8 et Xtream Codes API**, de retrouver ses playlists et préférences sur les appareils pris en charge, puis de consulter et lire tout le contenu réellement fourni : **chaînes Live, films VOD, séries, saisons et épisodes**.

Le produit se veut une expérience moderne de consultation de catalogue, comparable en fluidité et hiérarchie à une application de streaming premium, **sans prétendre être Netflix**, avec une identité ZYVIOTV propre. Ne pas créer de contenu IPTV fictif, de titres, d'épisodes, de logos ou de données EPG inventés.

**Distinction des produits :**
- `ZyvioTV-Player` est l'application de lecture multiappareils ; le repo ZYVIOTV du site public/catalogue/admin est un projet distinct.
- Le lecteur partage les services d'authentification ZYVIOTV prévus mais n'hérite pas automatiquement des règles, tables, catalogues, designs ou contraintes du site web.
- Les providers M3U/Xtream ajoutés par l'utilisateur sont des sources de contenu externes. Leur disponibilité réelle dépend des droits et des réponses du fournisseur. Ne pas contourner les protections d'accès ou DRM.

## 2. Plateformes et architecture validées dès le démarrage

| Plateforme | UI / runtime cible | Moteur de lecture prévu | Particularités |
|---|---|---|---|
| Android téléphone | Jetpack Compose | Media3 / ExoPlayer | Navigation basse, touch, fenêtres compactes |
| Android tablette | Jetpack Compose | Media3 | Portrait/paysage, panneaux et navigation adaptés |
| Android TV / Google TV / box Android | Compose avec interactions TV | Media3 | Télécommande D-pad, focus visible, retour et zapping |
| iPhone | SwiftUI + cœur Kotlin partagé | AVPlayer | Safe areas, navigation native |
| iPad | SwiftUI + cœur Kotlin partagé | AVPlayer | Layouts portrait/paysage |
| Samsung Smart TV | Client Tizen dédié | Lecteur adapté à Tizen | Focus et télécommande, packaging spécifique |
| LG Smart TV | Client webOS dédié | Lecteur adapté à webOS | Focus et télécommande, packaging spécifique |

**Architecture de référence :** Kotlin Multiplatform pour modèles, protocoles, validations, parsing M3U/Xtream, EPG, favoris, historique/progression, synchronisation et contrats d'erreurs ; interfaces et décodage/lecture vidéo **natifs à chaque plateforme**. Ne pas essayer de partager de force un lecteur vidéo incompatible. Favoriser la parité fonctionnelle sans imposer une UI identique entre TV et mobile.

**Emplacement de ce fichier :** `CLAUDE.md` à la racine du dépôt (ajouté par le bloc #207 ; il n'existait auparavant que dans les fichiers du projet Claude).

**Structure du dépôt vérifiée :** `.github/`, `app/` (Android), `shared/`, `iosApp/`, `tizenApp/`, `webosApp/`, `supabase/`, `docs/`, `tests/`, `scripts/`, Gradle à la racine. Inspecter le contenu exact à chaque tâche avant de nommer des classes/fichiers, car les chemins changent.

**Baseline Android déclarée par le README :** JDK 17, minSdk 24, targetSdk 35, compileSdk 36. L'émulateur de recette utilisé en octobre 2026 est Pixel 7 / Android 17 API 37.1. Ne pas confondre la version de l'émulateur et `compileSdk` du repo.

## 3. Design system ZYVIOTV : conserver le design Claude validé

- Identité **noir profond / rouge ZYVIOTV**, contrastes premium, accents rouges mesurés, typographie lisible. **Pas de design généré au hasard**, pas de relooking façon « IA », pas de changement spontané des composants déjà acceptés.
- Logos officiels : monogramme **Z** et wordmark **ZYVIOTV**, sans 3D superflue. Ne pas utiliser une simple substitution de texte ou un logo alternatif non validé.
- Fondations : grille d'espacement **4 dp/px selon la plateforme**, échelles typographiques adaptées à chaque classe, tokens de couleurs, rayons, états interactifs et focus accessibles.
- Classes d'écran du design : **Compact, Medium, Expanded, Large et TV**, selon taille et type d'appareil. Adapter colonnes, posters, marges, rail et safe areas, y compris 1080p/4K et orientations tablette.
- Composants prévus : boutons, cartes/posters, filtres/chips, barres de navigation, menus, dialogs, sheets, EPG, mini-player, loaders, empty states, erreurs, badges et états normal/focused/pressed/selected/disabled.
- Sur TV, **focus blanc visible** conformément au correctif validé ; navigation complète à la télécommande, sans blocage d'un focus invisible.
- Flux de référence conçus avec Claude Design : splash, login/signup, reprise de session, accueil, recherche globale, TV direct, guide EPG, détails films/séries, lecteur, paramètres/compte, playlists, appareils, profils, préférences et contrôles parentaux.
- Maquettes sources `*.dc.html` et captures de référence sont à rechercher parmi les fichiers/designs disponibles ; **ne pas inventer leur présence dans le repo**. En cas de doute, vérifier les designs existants avant modification.
- Références de design connues : accueil mobile ~390×844 avec bottom navigation ; tablette ~768×1024 avec rail latéral et posters adaptés ; TV 1080p en grille/rails. Ce sont des **références de conception**, pas des dimensions à coder en dur.
- La cohérence des libellés FR et des traductions FR/EN doit être vérifiée sur tous les écrans. Un écran « My account » en anglais alors que l'app est en français est une anomalie signalée.

## 4. Parcours utilisateur et règles fonctionnelles historiques

### Compte, profils, multi-appareils

- Inscription/connexion par compte ZYVIOTV existant (Supabase Auth), session locale chiffrée, reconnexion et retour à l'accueil à la réouverture si la session est toujours valide.
- Profils individuels, profil par défaut, changement de profil, préférences audio/sous-titres/qualité/langue, lecture automatique de l'épisode suivant selon préférence, contrôles parentaux/PIN et états adaptés à chaque appareil.
- Parcours TV pouvant comprendre la connexion par **QR / code TV**, états code expiré, succès, erreurs, retour et focus ; conserver les règles d'auth TV validées dans les maquettes.
- Playlists enregistrées côté compte, portabilité multi-appareils : **ne pas obliger à les ressaisir** en changeant d'appareil. Protéger les données d'accès et les informations affichées.
- Compte/appareils et synchronisation des sessions doivent rester limités au propriétaire authentifié. Le 403 `player_devices` a été résolu le 09/10/2026 (voir §5).

### Formats M3U et Xtream : parité obligatoire

- **Pour chaque format sur chaque plateforme prise en charge :** TV live, films VOD, séries, saisons/épisodes disponibles et lecture des vrais flux ; ne pas traiter Xtream comme « chaînes seulement », ni M3U comme « live seulement ».
- M3U : prise en charge des lignes `#EXTM3U`, `#EXTINF`, attributs `tvg-*`, `group-title`, URLs de flux, variantes/gzip si présentes ; reconnaître les catégories/genres selon données fournisseur. La classification Live/Film/Série doit utiliser les informations fiables de la source et des règles testées, et éviter les faux positifs.
- Xtream : validation/auth via `player_api.php`, catégories/streams Live, VOD et Séries, saisons et épisodes réellement fournis, détails, noms, logos, métadonnées, EPG si présent. URL de serveur pouvant contenir protocole, port, sous-chemin ou terminaison `player_api.php`/`get.php`.
- Conserver la prise en charge HTTP ou HTTPS **lorsque le fournisseur l'exige**, avec périmètre réseau minimal ; ne pas imposer une migration HTTPS impossible ni désactiver globalement des protections Android sans justification.
- Ne pas forcer les requêtes à des ports inventés (le fournisseur testé n'indique pas de port particulier).
- Ne pas annoncer une playlist importée « prête » si parsing/stockage incomplets. Conserver **atomiquement l'ancien catalogue complet** si rafraîchissement échoué ou annulé.
- Ne pas tronquer silencieusement les gros catalogues (`maxEntries`, limite de cache) ; si une limite technique est explicite, l'exposer et la tester. Ne pas confondre nombre de titres, nombre d'épisodes et nombre d'entrées M3U.

### Catalogue et navigation

- Accueil, récemment ajoutés/continue watching suivant sources et préférences réelles ; TV live, Films, Séries, recherche globale, favoris, historique, reprise, prochain épisode.
- Recherche insensible à la casse et aux accents, récents locaux **10 maximum** selon la conception validée, badge source en contexte multi-playlists (P2, etc.). Recherche et catégories adaptées au clavier TV.
- TV direct : catégories, liste chaînes, mini-player/lecteur, zapping, guide « maintenant », EPG/timezones, informations du programme, retour à maintenant, rappel quand réellement disponible ; le guide indisponible n'empêche pas de lire une chaîne.
- Films : grille poster, détails, disponibilité réelle de lecture, favoris/historique/reprise.
- Séries : détail, saisons, épisodes réellement disponibles, progression, prochain épisode, reprise et bouton lecture fonctionnels.
- Hors connexion : montrer le **catalogue validé en cache** ; masquer ou désactiver seulement les actions qui exigent réellement le réseau, avec explication fidèle à l'état. **La réussite d'un GET M3U ne prouve pas que tous les flux vidéo fonctionneront**, mais une règle « hors ligne » erronée ne doit pas bloquer tous les lecteurs.
- Aucun fallback fictif ne doit faire croire à une lecture réussie ou à un programme EPG réel.

## 5. Supabase, permissions et données sensibles

- Supabase de production est partagé avec ZYVIOTV ; distinguer schémas/migrations du dépôt, API effectivement exposée, `GRANT`, RLS, politiques et comportement en production.
- Au cours des recettes : exposition **sélective** de `public.player_profiles` et `public.player_playlists` pour résoudre d'anciens 403/401. `player_playlists` : politiques RLS propriétaires pour SELECT, INSERT, UPDATE et DELETE et privilèges `authenticated` vérifiés lors des tests.
- L'enregistrement des secrets de playlists repose sur une couche sécurisée et des RPC, notamment `player_set_playlist_secret` et `player_get_playlist_secret`. La table `player_playlist_secrets` ne doit **JAMAIS être exposée directement** via Data API.
- Le 9 octobre 2026 : `player_playlists` GET 200, création 201, set secret 204, get secret 200 sur le Pixel 7. **Cela ne prouve pas que toutes les tables et RPC fonctionnent.**
- Ancienne anomalie `player_devices` 403 (`authError=permission-denied`) : résolue le 09/10/2026 par un GRANT ciblé à `authenticated` après audit en lecture seule (voir §8 et §11). Méthode à reproduire pour tout futur 403 : audit ciblé de l'exposition API, des grants effectifs, des politiques RLS, schéma et utilisateur auth ; migration minimale revue ; **aucun changement de prod sans validation explicite**.
- Ne jamais « corriger » un 403 en désactivant RLS, accordant `anon` à des données privées, autorisant `public`, ou exposant toutes les tables/fonctions.
- Sur clients, respecter le rafraîchissement de session authentifiée (PR #204), le retry raisonnable et des erreurs distinctes (401/403 vs réseau), sans journaliser de tokens.
- En multi-playlists, préserver l'isolation par compte, playlist et profil. Tester le comportement lors de déconnexion, reconnexion et changement de profil.

## 6. Synchronisation, cache, performance et stabilité

- La synchronisation de playlists volumineuses doit être **progressive, annulable, dédupliquée** et réalisée hors du thread principal.
- Le cache catalogue doit être **persistant, chiffré, compressé, validé et atomique** ; afficher la dernière version complète dès le redémarrage. Les réactualisations peuvent courir en arrière-plan, sans forcer un re-téléchargement à chaque onglet.
- Ne pas démultiplier en RAM des listes complètes pour chaque filtre, écran, transformation, séquence ou composant d'état. Utiliser structures appropriées, pages/flux si besoin, filtrage efficace et rendu Compose `LazyColumn`/`LazyVerticalGrid` avec clés stables.
- Éviter composition simultanée de milliers de noeuds, copies dupliquées de listes, `FocusRequester` par entrée hors écran, recréation intempestive d'expressions régulières, décompression JSON disproportionnée.
- Mesurer séparément `catalog_cache_load`, téléchargement, parsing, persistance, affichage/category open et mémoire/GC. Logs **anonymisés** via `ZyvioCatalog` ; réseau via `ZyvioNetwork`.
- Échec réseau/transfert : différencier HTTP, délai, déconnexion, corruption/troncature, exception I/O, annulation et erreur de parsing. Ne pas transformer une importation partielle en succès et ne pas faire des retries infinis.
- Mesurer le coût réel de l'UI, qui peut être plus lent que le calcul de catégorie reporté par `duration_ms`.
- Une build verte ou un benchmark JVM rapide ne valide **pas** absence d'ANR/OOM sur Android 17 ou sur téléviseur physique.

## 7. Lecture multimédia — priorité produit

- Sur Android : examiner intégration Media3/ExoPlayer effectivement présente, création/release du player, lifecycle Compose, Source/MIME HLS `.m3u8`, MPEG-TS `.ts`, MP4 et autres formats explicitement supportés, HTTP headers/redirections, erreurs de décodeur/réseau, configuration de buffer raisonnable, audio/subtitles et contrôles du player.
- Sur iOS : AVPlayer natif et équivalences de comportement réelles. Sur Tizen/webOS : moteur de plateforme, capacités et limites documentées ; ne pas promettre un codec non pris en charge.
- Une pression sur une chaîne, un film ou un épisode doit avoir un **callback réel** relié au bon flux et produire un état observable : ouverture/mini-player, chargement, lecture, échec explicite. Pas de bouton silencieux ou de faux succès.
- Ne pas conditionner toute lecture à l'existence d'un EPG, à la disponibilité de Supabase `player_devices`, ou à un état de connectivité imprécis ; vérifier connectivité et réponse du flux lorsque nécessaire.
- Ne pas divulguer les URL de flux contenant credentials dans l'UI d'erreur ou dans Logcat.
- Préserver favoris/reprise/historique de visionnage lorsque la lecture est réellement possible et synchronisée selon les contrats.

## 8. Historique de développement et jalons connus

**Phase initiale (oct. 2026)**
- Conception premium noir/rouge, design d'écrans mobile/tablette/TV avec Claude Design ; choix KMP, Compose Android, SwiftUI iOS, lecteurs natifs et backend ZYVIOTV ; authenticaton, profils, persistance de session, playlists, recherche, TV/EPG, catalogue, paramètres et adaptation TV.
- Premières dizaines de phases et PR couvrant les écrans, modèles, services, flux et clients des différentes plateformes. Les détails exhaustifs sont à reconstruire depuis `docs/PHASES.md`, GitHub PR/commits et les maquettes ; **ne pas attribuer une fonctionnalité à un numéro de PR sans vérifier**.
- Livraisons successives Android/iOS/TV jusqu'aux PR #201 (protocole de recette multiplateforme), #202 (diagnostics profils Android/Supabase), puis tests manuels Pixel 7.

**PR #203 — HTTP et interface Android**
- Ajustements d'autorisation HTTP cleartext suivant les cas légitimes, messages/UI contraste, logs sans secrets. Avant, Xtream et M3U pouvaient échouer à cause de la politique réseau.

**PR #204 — Diagnostics réseau / Supabase**
- Auto-refresh/retry sécurisé sur 401 et diagnostics `ZyvioNetwork`. Accès `player_playlists` par la suite validé HTTP 200. Ne pas rouvrir les problèmes 401 anciens sans reproduction.

**PR #205 — Robustesse réseau M3U et Xtream**
- Parsing en flux, gzip/gros catalogues, détection interruption/troncature, retries bornés, conservation de version complète, normalisation Xtream ports/sous-chemins, cookies/redirections, tests jusqu'à 50k entrées. La cause exacte de l'ancien `m3u failure=io` ne peut pas être rétrospectivement affirmée.
- Sur Pixel 7, première synchronisation réussie mais environ **5 minutes**, pression mémoire importante et plus tard crash lors de l'utilisation du catalogue.

**PR #206 — Grands catalogues et mémoire**
- Fusionnée dans `main`, SHA `c8f9fd3819056434474fda56eafaddfe3abd68bd`, CI après fusion signalées vertes.
- Cache persistant chiffré/compressé, restauration, refresh arrière-plan mutualisé, catalogues moins copiés, listes Compose paresseuses, logs performance anonymes ; tests JVM Android et garde-fous Tizen/webOS.
- **Mesures Pixel 7 après #206**, playlist réelle : **6 074 chaînes**, **12 607 films**, **4 160 séries**, total **22 841 éléments**. Lecture du cache observée ~**11–24 secondes** à l'ouverture, parfois **deux chargements simultanés** loggés. Ouverture logique de catégorie souvent 0–1 ms, mais jank et frames ignorées persistent. Téléchargement refresh M3U observé ~**26–30 secondes**. Aucun OOM observé dans cette courte recette #206.
- Anomalies observées : faux bandeau « Mode hors connexion » malgré réponses HTTP 200 ; lecture TV et VOD désactivée/absente ; posters parfois vides ; classifications suspectes (ex. une chaîne sport dans Séries) ; textes anglais résiduels ; `player_devices` 403. Ces anomalies **ne sont pas réglées** par le simple fait que #206 soit verte.

**Crash historique avant #206 (preuve sur Pixel 7)**
- `FATAL EXCEPTION: main`, `java.lang.OutOfMemoryError` à ~191/192 Mo, stack Jetpack Compose `SemanticsConfiguration` / `LayoutNode.attach` / `ScaffoldLayout` le **09/10/2026 ~18:03:14**. Des ANR/frames sautées ont été observées. Le haut de stack OOM désigne le lieu d'échec d'allocation, pas nécessairement l'origine de toutes les allocations.

**Bloc #207 — Lecture Android Live / VOD / Séries** (PR #207 fusionnée, `main` = `65b9fa8cf967ce92994ae9f114c7f058ef6827d1`)
- Symptômes Pixel 7 : appuyer sur une chaîne ne faisait qu'une sélection ; fiche film « Lecture et modification des favoris indisponibles hors connexion » malgré des réponses HTTP 200.
- Causes vérifiées dans le code : (1) la carte chaîne n'appelait que `onChannelSelected` ; (2) « hors connexion » n'était pas la connectivité : `ProviderCatalogState.Ready.isOffline` valait vrai dès qu'un catalogue restauré n'avait pas d'URL de flux (cache JSON hérité d'avant #206), et `LibrarySnapshot.isOffline` dès qu'une requête bibliothèque Supabase échouait ; tous les callbacks de lecture retournaient alors **sans message**. Le déclencheur exact sur le Pixel 7 (cache hérité, échec de synchronisation compte) reste à confirmer par Logcat.
- Décisions : la connectivité vient uniquement de `NetworkAvailability` (`ConnectivityManager`, état inconnu = non bloquant) ; `isFromCache` décrit l'origine des données et ne bloque jamais la lecture ; toute lecture passe par `PlaybackLaunchPolicy` (refus = source absente/invalide ou appareil sans réseau, toujours avec message FR) ; tap/OK sur une chaîne ouvre le lecteur, le focus D-pad ne fait que sélectionner ; épisodes M3U lus depuis le registre local avant tout appel Supabase ; Media3 avec user-agent ZYVIOTV, redirections HTTP↔HTTPS, repli HLS unique, erreurs typées (`tag:ZyvioPlayback`, sans URL).
- Cache #206 conservé (lecture du cache désormais mutualisée pour éviter le double chargement au démarrage). Aucun changement Supabase.
- Non vérifié à ce stade : lecture réelle sur Pixel 7 / téléphone / TV avec le fournisseur.

**Bloc #208 — Sources de lecture M3U** (PR #208 fusionnée, `main` = `c867b0292a5ee150bbb24eafb6ad86eceb642c26`)
- Symptôme Pixel 7 après #207 : tap/Lire ouvrent bien la décision de lecture, mais Logcat `ZyvioPlayback blocked reason=missingsource` pour tout le catalogue.
- Preuve dans le code : la seule origine possible d'une `streamUrl` vide est `OfflineContentCache.loadLegacyCatalog` (cache JSON SharedPreferences d'avant #206, qui n'a jamais stocké d'URL). Le parser M3U, le mapper, le codec #206 et la construction des `PlaybackRequest` conservent les URL (tests de bout en bout). Ce cache hérité restait chargé comme catalogue « prêt » quand le cache chiffré était absent ou illisible, et un échec d'écriture du cache chiffré était silencieux, donc l'ancien JSON n'était jamais supprimé. Le déclenchement exact sur le Pixel 7 reste à confirmer par la ligne `ZyvioCatalog catalog event=catalog_cache_sources origin=…`.
- Décisions : `CachedCatalog.origin` (`Encrypted` / `LegacyWithoutSources`) et `CatalogSourceReport` (comptes seulement) ; un cache sans sources est affiché mais `Ready.sourcesPending` bloque la lecture avec un message FR (`SourcesPending`) jusqu'à la synchronisation ; le cache n'est remplacé que par un catalogue dont toutes les sources sont présentes ; échecs de lecture/écriture du cache journalisés (`catalog_persist_failed`, `catalog_cache_unreadable`) ; l'ancien JSON est supprimé dès qu'un cache chiffré valide existe ; format `EncryptedCatalogFile` inchangé (extrait pour être testé en JVM).
- Paramètres d'accès : en-têtes M3U `url|User-Agent=…&Referer=…` (Kodi) et `#EXTVLCOPT:http-user-agent/http-referrer` conservés dans la ligne de flux (`PlaybackSource`), transmis à Media3 comme en-têtes HTTP (liste blanche User-Agent/Referer/Origin, jamais journalisés) ; requêtes/jetons d'URL inchangés.
- Recette Pixel 7 rapportée par l'utilisateur après fusion : lecture TV et Films via M3U confirmée ; épisodes démarrent mais erreur décodeur HEVC sur l'émulateur.

**Phase #209 — Fiabilisation du lecteur Android** (PR #209 fusionnée, `main` = `8d4b96f295c6d5f54afa63d29ebac2c37735193c`)
- Retour : le bouton haut du lecteur était dessiné sous la barre d'état (app edge-to-edge, aucun inset) et la sortie remettait `playbackRequest` à null, ce qui recomposait l'entrée sortante dans sa branche « sans requête » et dépilait une seconde fois ; le retour système n'avait pas de `BackHandler` et ne sauvegardait pas la progression. Désormais : un seul chemin (`PlayerBackPolicy`) pour Retour et le retour Android, sauvegarde puis un seul `popBackStack` gardé par `PlayerExitNavigation.shouldPop`.
- Media3 : libération unique dans `DisposableEffect(player)` (fermeture ou changement de chaîne/épisode, log `release reason=dispose`), `PlayerView.player = null` à la libération de la vue, commandes à jeton non rejouées sur un nouveau lecteur (`PlayerCommandGate`), reprise au premier plan seulement si la lecture tournait (`PlayerResumePolicy`), callbacks via `rememberUpdatedState`.
- Contrôles : insets `safeDrawing`, titre sur une ligne avec ellipse, actions icône seule sous 600 dp (description accessible), rangée défilante au lieu d'un retour à la ligne, temps écoulé/durée, bouton « Suivant » retiré pour les films (aucune cible), Retour disponible pendant la mise en mémoire, erreurs empilées en portrait. Plein écran réel (barres masquées + paysage capteur, restauration à la sortie) et mode d'image Ajuster/Remplir (`RESIZE_MODE_FIT`/`ZOOM`, jamais d'étirement). Pas sur TV.
- Pistes : sélection par identifiant Media3 (`TrackSelectionOverride`), y compris pistes sans langue.
- HEVC : repli décodeur Media3 activé ; `DecoderDiagnosis` (mime, codec, émulateur) dans le log `failure` et message FR distinguant limite d'émulateur et appareil.
- Jank : position publiée 1×/s seulement en lecture ; synchronisation de progression toujours limitée à 15 s (et une fois à la fin).
- Recette Pixel 7 rapportée après fusion : Retour du lecteur et retour Android validés, `release reason=dispose` observé, cache chiffré retrouvé avec toutes les sources ; **bouton plein écran invisible** (corrigé par #210).

**PR #210 — Plein écran, orientation et arrêt Media3** (PR #210 fusionnée, `main` = `da7a9de`)
- Cause racine du plein écran invisible : la route lecteur n'a aucune `Surface` au-dessus d'elle (NavHost directement sous `MaterialTheme`), donc `LocalContentColor` valait le noir par défaut de Compose. Le bouton plein écran (et Ajuster/Remplir) était un `IconButton` sans teinte : icône noire sur fond noir, présente et cliquable mais invisible. Les boutons visibles (Retour, actions du bas) avaient une couleur explicite.
- Correctif : `PlayerScreen` fournit `LocalContentColor = ZyvioTextPrimary` ; bouton Plein écran (icône agrandir/réduire, teinte explicite, cible 48 dp) fixe à droite de la rangée du bas, hors défilement, pour Live, Films et Épisodes ; Ajuster/Remplir en haut à droite.
- Plein écran (`PlayerFullscreenPolicy`, téléphone/tablette, jamais TV) : le bouton masque les barres et demande le paysage capteur ; un téléphone tourné en paysage est aussi en plein écran ; quitter en paysage demande le portrait ; Retour quitte d'abord le plein écran puis le lecteur ; l'orientation d'origine et les barres sont restaurées à la sortie du lecteur. Téléphone détecté par `smallestScreenWidthDp < 600` (un Pixel 7 en paysage passe au profil Tablet). La rotation ne recrée pas l'activité (`configChanges`), donc le flux n'est pas relancé.
- Media3 / MediaCodec : la surface `PlayerView` est détachée avant l'unique `release()`. Les avertissements `Handler sending message to a Handler on a dead thread` (LegacyMessageQueue / MediaCodec, `c2.goldfish.*`) surviennent après `ExoPlayerImpl Release` : rappels tardifs du codec de l'émulateur vers le thread de lecture déjà arrêté, journalisés par le framework. Aucune double libération ni rappel applicatif trouvé dans le code ; à recontrôler sur appareil réel.
- Non vérifié : Pixel 7, téléphone réel, tablette, TV.

**Mesures Pixel 7 à traiter dans la phase Performance (rapportées le 09/10/2026, après #209)**
- Lecture du cache chiffré **46 971 ms** ; sources `playable=true` ; chaînes 6 074/6 074, films 12 607/12 607, épisodes 117 989/117 989.
- Téléchargement M3U 27 401 ms, parsing 65 313 ms, sauvegarde du catalogue 45 611 ms.
- GC très fréquents, `Skipped 297 frames`, plusieurs `Davey` > 2 s dont un > 5 s.
- Resynchronisation complète du catalogue peu après le chargement d'un cache exploitable : à auditer.
- Supabase `player_devices` HTTP 403 : cause identifiée après #211 (voir ci-dessous).

**PR #211 — Télémétrie, travail inutile, stabilité, intégrité, UX** (PR #211 fusionnée, `main` = `f1e359195180ccb207e7e84a58aec8be0a01943a`, CI post-fusion verte). Rapport d'audit : fichiers du projet `audits/audit-211.md`.
- Découpage validé par l'utilisateur : #211 = mesurer, supprimer le travail inutile, intégrité, UX ; bloc suivant = stockage paginé/générationnel (Room) et objectif de démarrage < 2 s (chiffrement AES-GCM par champ des URL de lecture, clé Keystore). Ce bloc a pris le n° **#213** (PR A), #212 ayant servi aux titres et au GRANT. #211 ne promet pas de démarrage plus rapide à froid.
- Rafraîchissement : `CatalogRefreshPolicy` (fraîcheur 12 h ; démarrage/changement de profil = automatique, Réessayer/playlist/parental = manuel, toujours exécuté). Aucune synchronisation automatique ne démarre pendant une lecture (`PlaybackActivity`, attente avant le démarrage ; jamais de parseur suspendu). Log `refresh_decision`.
- Date de fraîcheur authentifiée : fichier `<digest>.meta` AES-GCM (clé Keystore, AAD dédiée) liant la date au SHA-256 et à la taille du catalogue, écrit seulement après le catalogue. Format catalogue V1 inchangé et toujours lu ; aucun cache supprimé. Un catalogue remplacé/corrompu n'est jamais « attesté ».
- Vérification hors ligne au démarrage sur `Dispatchers.IO`, une fois par restauration ; états Live/Films/Séries construits hors thread principal (`CatalogUiStateCache`) ; accueil calculé une fois par catalogue ; `CatalogSingleFlight` libère une tâche terminée.
- Ids M3U : id 32 bits conservé s'il est unique ; en collision, un seul garde l'id (table d'alias, sinon plus petite empreinte 64 bits, indépendant de l'ordre), les autres reçoivent `m3u-<16 hex>` ; alias dans les métadonnées authentifiées.
- Xtream : validation de chaque liste ; HTML/objet JSON/illisible, aucune entrée exploitable, ou liste vide alors que le catalogue précédent de la même playlist ne l'était pas → échec, l'ancien catalogue et son cache sont conservés.
- Télémétrie (anonyme, bornée, sans log périodique) : GC/mémoire native dans les phases `ZyvioCatalog` ; `ZyvioUi frames screen=…` une ligne par visite d'écran ; `ZyvioPlayback summary …` une ligne par lecture à la libération ; `ZyvioNetwork` Supabase avec `sqlstate=` et `pg=` (`missing-table-grant` / `rls-violation`…) ; Xtream une ligne par saut HTTP (`profile= hop= redirect= content= set_cookie=`). Pas de modification `LoadControl`.
- UX : affiches dans les grilles Films/Séries (aucune requête si URL vide, repli icône+titre), logos de chaînes, titres films/séries nettoyés à l'affichage seulement (`DisplayTitle`), contrôle Ajuster/Remplir distinct du plein écran.
- Aucun changement Supabase. Non vérifié : Pixel 7, appareil réel, TV, fournisseur réel.

**Recette Pixel 7 émulateur après #211** (rapportée le 09/10/2026, `main` = `f1e3591`)
- Vérifié sur émulateur : affiches Films/Séries et logos de chaînes affichés ; lecture Live MPEG-TS (`ZyvioPlayback summary … first_frame_ms=5652`), `release reason=dispose` ; lignes `ZyvioUi frames` par écran.
- `catalog_cache_load` 35 166 ms (47 s avant), `ui_map_movies` 1 519 ms, `ui_map_series` 823 ms, `ui_map_live` 374 ms hors thread principal ; démarrage encore `Skipped 297 frames`, splash `frozen=1 max_ms=4651` (objet de #212).
- Premier lancement après mise à jour : `catalog_cache_freshness status=missing` puis `refresh_decision decision=unknown_age` → resynchronisation attendue (cache écrit avant #211, sans `.meta`). Confirmé au lancement suivant : `status=valid age_min=8` et `decision=use_fresh_cache`, sans téléchargement M3U (cache lu en 40 867 ms : démarrage toujours lent, objet du bloc Room).
- `player_devices` : `sqlstate=42501 pg=missing-table-grant` → privilège de table manquant pour `authenticated` (pas un refus RLS). La migration du dépôt crée la table et les politiques propriétaires mais aucun `GRANT` ; GRANT ciblé à `authenticated` (select/insert/update/delete, rien pour `anon`, RLS conservée) **appliqué en production par le propriétaire le 09/10/2026** après vérification en lecture seule ; migration `20261009230000_player_devices_authenticated_grant.sql`. Vérifié : `player_devices response=201` sur Pixel 7 émulateur.
- Titres « Animals (MULTI) FHD 2026 » non nettoyés : l'année finale bloquait `DisplayTitle` (correctif « Titre (2026) » sur la branche `claude/fix-android-playback-xru14n`). Classification : « |BH| ARENA SPORT » apparaît dans Séries (P1 ouvert).

**PR #212 — Titres avec année finale + GRANT `player_devices`** (PR #212 fusionnée, `main` = `394ba49`, CI post-fusion verte)
- « Animals (MULTI) FHD 2026 » → « Animals (2026) » (`DisplayTitle`). Migration `20261009230000_player_devices_authenticated_grant.sql` (GRANT déjà appliqué en production par le propriétaire). Vérifié sur Pixel 7 émulateur : titres nettoyés, `player_devices` 201 puis 200.

**Bloc Room #213 — stockage générationnel paginé** (plan validé par l'utilisateur le 10/10/2026)
- Décisions : (1) contrôle parental appliqué **à la lecture** (catalogue brut stocké, filtré dans les requêtes, PR B) ; (2) suppression du cache V1 dès qu'une génération Room complète est active, **au plus tôt avec la PR B** (la PR A lit encore le V1) ; (3) découpage A (socle), B (lecture paginée, démarrage < 2 s visé), C (écriture en flux pendant l'analyse).
- **PR A (#213)** : Room 2.7.2 + KSP, base privée `catalog-store.db` (`data/store/`). Tables `generation` (`building`/`active`/`retired`), `category`, `live_channel`, `movie`, `series`, `series_detail`, `episode`, `id_alias`, lignes clés par ordre fournisseur (`ordinal`) pour qu'un id dupliqué ne fasse jamais échouer un import. Écriture par lots transactionnels de 1 000, vérification de chaque compte, puis bascule `active` en **une seule transaction** ; l'ancienne génération est supprimée ensuite ; une génération incomplète (échec, annulation, processus tué) est jetée au prochain passage.
- Chiffrement : URL de lecture (avec en-têtes Kodi) en `iv | AES-GCM`, AAD `generationId|kind|id`. **Enveloppe** : une clé de données AES-256 aléatoire par génération, enveloppée par la clé Keystore `zyviotv_player_catalog_url_key` (évite ~140 000 opérations Keystore par import). Titres, catégories, affiches et ids restent en clair dans la base privée (`allowBackup="false"`).
- Remplissage en arrière-plan (`CatalogStore`) : import unique du V1 restauré (portée `profile_filtered`, jamais par-dessus une génération plus récente ou identique) et génération brute (`raw`) après chaque synchronisation validée. Aucun démarrage pendant une lecture. Logs `ZyvioCatalog` : `catalog_store_write`, `catalog_store_generation` (comptes, `db_size_mb`), `catalog_store_skipped`, `catalog_store_rejected`, `catalog_store_failed`. **L'app lit toujours le cache V1** ; rien n'est supprimé.
- Recherche : la recherche actuelle est un `contains` sur titre + catégorie (+ numéro de chaîne). FTS4 ne fait que des préfixes de mots : la PR A stocke donc une colonne `search_key` normalisée (même normalisation que `CatalogSearchEngine`) ; le choix LIKE/FTS sera tranché et mesuré en PR B.

**Xtream fournisseur réel — problème ouvert**
- Même abonnement déclaré fonctionnel en M3U **et Xtream** sur IPTV Smarters Pro, ainsi que Zen IPTV et SET IPTV.
- Android ZYVIOTV Player a reçu HTTP **512** (code non standard) à l'authentification Xtream ; racine serveur dans Chrome affichait « Prohibited ». Ces symptômes ne déterminent pas seuls si le blocage est fournisseur, proxy, headers, format d'URL ou client.
- PR #205 comporte des tentatives de compatibilité mais aucun test confirmé sur fournisseur réel après correctif. Reste à tester et diagnostiquer sans contourner les contrôles.

## 9. Processus de développement GitHub / CI

1. Inspecter `git status`, branche courante, `git log`, dernier `origin/main`, PR ouvertes et `CLAUDE.md`/docs existants. Ne jamais écraser les modifications non commitées ou les contributions d'autrui.
2. Énoncer le périmètre, hypothèses et scénario minimal de reproduction. Identifier les vrais appels, données et composants avant de changer le code.
3. Créer une branche ciblée `fix/...` ou `feat/...`, courte, issue du dernier `main`. Conserver les commits propres et la revue facile.
4. Ajouter des tests unitaires (shared/KMP), Android instrumentation/intégration si disponible, tests réseau mockés, tests mémoire/volumes, et tests de navigation/télécommande selon le périmètre. Ne jamais dépendre de secrets fournisseur dans une CI publique.
5. Respecter Gradle, lint, format et jobs réels du dépôt. Contrôler **Shared + Android CI**, builds/debug/release, **Release Gate**, **iOS CI**, validations **Tizen/webOS** applicables. Les noms exacts des workflows doivent être vérifiés dans `.github/workflows`.
6. Ouvrir une PR descriptive : origine du problème, fichiers affectés, stratégie, tests réels exécutés, résultats, limites, rollback et risques de sécurité. Ne pas annoncer des résultats de tests non exécutés.
7. Attendre le vert de **tous les contrôles obligatoires sur le SHA courant**, résoudre les reviews et conversations. Ne pas forcer le merge ou désactiver des contrôles.
8. Après fusion, vérifier SHA effectif de `main`, pipelines post-merge, et donner instructions concrètes pour Android Studio (branch `main`, Git Pull, Gradle Sync, `Run app`, filtre Logcat).
9. Mettre à jour `docs/PHASES.md`/docs de plateforme et ce fichier avec les changements réalisés, puis faire une liste d'anomalies restantes et next phase.

## 10. Qualité de recette par plateforme

- **Android mobile** : Pixel 7 Android 17 émulateur puis vrai téléphone, authentification, M3U/Xtream, cache, consultation catégories, Live/VOD/épisodes, lectures réelles, retour, reprise, offline/online, rotation, performances/ANR/OOM.
- **Android tablette** : variantes portrait/paysage, rail, affichage grilles, lecteur et background/foreground.
- **Android TV / box** : D-pad, focus blanc visible, 1080p/4K, clavier TV, zapping, EPG, fermeture overlays, télécommande, lecture réelle et codecs.
- **iOS/iPadOS** : SwiftUI, AVPlayer, stockage/session, formats IPTV, modèles KMP, comportements hors ligne, appareil réel/Simulator quand disponible ; ne pas assimiler « CI verte » à « App Store signé ».
- **Samsung Tizen / LG webOS** : build/package CI et validation sur appareils avec télécommande, login, listes, lecture, cycles de vie et performances. Ne pas déclarer un déploiement store certifié sur la base de simples tests statiques.
- **Multiplateforme** : une playlist doit restaurer la même disponibilité du contenu et les préférences pertinentes, sans secrets dans le catalogue public/cache non chiffré ; tenir compte des limitations réelles des codecs et API.

## 11. Points ouverts, classés par priorité (état au 09/10/2026)

**P0 — indispensable avant toute déclaration d'application opérationnelle**
- Corrigé dans le code par #207, à recetter sur Pixel 7 : ouverture du lecteur au tap sur une chaîne, boutons Lire VOD et épisode, faux « hors connexion ».
- Corrigé dans le code par #208, à recetter sur Pixel 7 : `blocked reason=missingsource` (cache hérité sans URL), en-têtes d'accès M3U, redémarrage sans perte des sources.
- #209 validé sur Pixel 7 pour Retour/retour Android et libération Media3 ; à recetter : pistes, message HEVC émulateur.
- Corrigé dans le code par #210, à recetter sur Pixel 7 : bouton Plein écran visible, paysage/portrait, Retour depuis le plein écran, Ajuster/Remplir.
- #211 recetté sur Pixel 7 émulateur (affiches, logos, `ZyvioUi frames`, `ZyvioPlayback summary`) ; `refresh_decision decision=use_fresh_cache` confirmé au 2ᵉ lancement ; reste l'icône Ajuster/Remplir.
- Vérifier flux en lecture réelle, compatibilité HLS/TS/MP4, HEVC sur appareil physique, erreurs et retour (Logcat `tag:ZyvioPlayback`).

**P1 — fiabilité/sécurité**
- `player_devices` HTTP 403 Supabase : `pg=missing-table-grant` (42501), GRANT ciblé appliqué par le propriétaire le 09/10/2026 ; **résolu** (`response=201` sur émulateur, migration #212).
- Xtream réel HTTP 512 encore non résolu/non retesté sur #205+ ; ne pas supposer que l'URL ou le fournisseur est mauvais.
- Performance : cache chiffré 35 à 77 s à froid, parsing 65 s, sauvegarde 46 s, GC fréquents, `Skipped 297 frames`, `Davey` > 5 s (voir mesures §8). Resynchronisation après cache valide traitée par #211 ; démarrage < 2 s visé par le bloc Room #213 (PR B), la PR A ne change pas encore le démarrage.
- Classification M3U à vérifier, notamment chaînes sport apparaissant comme séries ; distinguer source/mapping et alias.

**P2 — UX et parité**
- Posters VOD/séries : grilles validées sur émulateur après #211 ; détails incomplets ; titres avec année finale corrigés après #211 (à recetter).
- Traductions résiduelles (ex. « My account »), intégrité FR/EN.
- Guide EPG, favoris/historique, lecture continue, recherche, profils, contrôles parentaux, QR TV, multi-appareils : refaire une recette réelle par plateforme ; ne pas prendre les maquettes pour des tests d'exécution.

## 12. Format obligatoire du rapport Claude Code

À la fin de chaque mission répondre en français avec :

- **Statut** : codé / PR créée / CI vertes ou non / fusionnée ou non / testé Pixel 7 ou non.
- **PR et branche**, lien GitHub, SHA head et SHA `main` si fusion.
- **Cause racine** : preuve observée, alternatives encore ouvertes, limites du diagnostic.
- **Corrections** : composants principaux et comportement désormais attendu.
- **Tests** : commandes exécutées, scénarios, volume, résultats, workflows et liens. Distinguer benchmark JVM des essais émulateur/fournisseur.
- **Sécurité** : tables/RLS/exposition, données sensibles, éventuelles migrations (ou « aucune »).
- **Actions utilisateur** : étapes exactes et courtes pour Git Pull/Run/Logcat, sans exiger copie manuelle de code.
- **Reste à faire** : anomalies prioritaires et bloc suivant proposé.

## 13. Première tâche au prochain lancement de Claude Code

1. Lire ce fichier puis vérifier que les chemins et l'architecture correspondent au `main` actuel.
2. Vérifier sur GitHub l'état du bloc Room (#213 = PR A, puis PR B et C) et la recette Pixel 7 rapportée par l'utilisateur (lignes `catalog_store_generation`, `catalog_store_write`, `catalog_cache_load`) avant d'ouvrir un nouveau bloc.
3. Prochains candidats : PR B du bloc Room (écrans sur Room, démarrage < 2 s visé), PR C (écriture en flux), Xtream HTTP 512, classification M3U, puis bloc « fidélité design Android » (Manrope, barre basse D6, onglet inactif, i18n).
4. **Ne pas commencer** par changer la base Supabase de production, ajouter un `largeHeap`, inventer des streams, réécrire les maquettes ou prétendre tester le fournisseur depuis CI.

---

**Principe directeur :** ZYVIOTV Player n'est pas « terminé » quand le catalogue s'affiche ; il l'est pour une plateforme donnée seulement quand une source M3U et une source Xtream réelles peuvent fournir et faire lire Live, Films et Séries de façon stable, sécurisée et fidèle au design validé, avec les limitations de chaque appareil correctement documentées.
