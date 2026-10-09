# Phase 193 — D7 preuves de recette finale (état au 9 octobre 2026)

**Important : aucun test sur appareil physique et aucune comparaison de captures réelles ne sont attestés par cette PR.** Les validations de source et les builds CI ne remplacent pas la recette D7.

## Références officielles
Archive reçue : `ZYVIOTV D3 M3UXtream (6).zip`.
- `D6_Final_Design_Handoff/ZYVIOTV D6 Design System Final.md`
- `D6_Final_Design_Handoff/ZYVIOTV D6 QA Checklist.md`
- `D7_Final_QA_Release_Readiness/ZYVIOTV D7 Matrice Recette Multiplateforme.md`
- `D7_Final_QA_Release_Readiness/ZYVIOTV D7 Tests Lecteur Video.md`
- `D7_Final_QA_Release_Readiness/ZYVIOTV D7 Tests M3U Xtream.md`

## Matrice de recette à exécuter

Statuts = **NON EXÉCUTÉ** sur Android téléphone/tablette/TV, iPhone, iPad, Tizen et LG webOS.

| Parcours | Priorité | Test d'acceptation | État appareil |
|---|---|---|---|
| P01–P03 | P0 | Premier lancement, connexion, profils | NON EXÉCUTÉ |
| P04 | P0 | M3U : TV + films + séries + épisodes réellement présents | NON EXÉCUTÉ |
| P05 | P0 | Xtream : TV + films + séries + épisodes réellement présents | NON EXÉCUTÉ |
| P06–P07 | P0 | Actualisation, Live, EPG et zapping | NON EXÉCUTÉ |
| P08–P09 | P0 | Films et saisons/épisodes ; aucune entrée inventée | NON EXÉCUTÉ |
| P10–P11 | P0 | Favoris, reprise et PIN par profil/playlist | NON EXÉCUTÉ |
| P12–P13 | P1 | Gestion appareils/playlists et reconnexion | NON EXÉCUTÉ |
| P14 | P0 | Hors ligne, échec réseau, reprise | NON EXÉCUTÉ |
| P15 | P1 | Bascule FR/EN | NON EXÉCUTÉ |
| P16 | P0 | Télécommande, focus blanc et retour hiérarchique | NON EXÉCUTÉ |

## Captures nécessaires
Pour chacun des sept types d'appareils : ouverture, connexion, accueil, films, séries/épisodes, lecteur, Live/EPG, favoris et paramètres. Capturer le même contenu et les mêmes états que la maquette D6 (état normal, focus, chargement, erreur). Inclure 720p, 1080p, 4K sur TV, orientation portrait/paysage sur tablette et mise à l'échelle texte. Conserver : modèle, OS, résolution logique, build SHA, capture, attendu, observé, résultat.

## Écarts et risques restant ouverts
- **Majeur — preuves manquantes** : aucune comparaison capture réelle ↔ maquette D6 sur appareil.
- **Majeur — plateformes** : P04/P05/P07/P08/P09 restent à exécuter sur chaque appareil avec playlists de test autorisées.
- **Majeur — TV** : focus initial, retour télécommande (Tizen 10009 / LG 461), overscan et retour d'un dialogue à vérifier.
- **Majeur — lecture** : codecs, pistes audio/sous-titres, timeshift et reprise à valider sur appareils.
- **Mineur — i18n** : vérifier toutes les chaînes FR/EN, pas seulement les modèles de démonstration.

## Correctifs de la PR
- LG : la navigation ferme maintenant aussi un éventuel panneau « Appareils » resté affiché en arrière-plan.
- Vérifications statiques D7 reproductibles ajoutées à la CI, sans prétendre avoir exécuté les tests physiques.

**Blocage publication :** ne pas déclarer la parité visuelle D1–D7 ni la compatibilité réelle tous appareils avant les captures et la recette physique.
