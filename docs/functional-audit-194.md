# Phase #194 — Audit fonctionnel multiplateforme : constats vérifiés

Base analysée : `main` à `4743207b230fe05cc851184f14109bd6f95fab55`.

## Périmètre vérifié sur source
- Samsung Tizen et LG webOS : `provider.js`, tests existants, flux M3U/Xtream et regroupement des saisons/épisodes.
- Contrats CI : `webos-ci.yml` et `tizen-ci.yml`, tests source D7 existants.
- Android, iOS/iPadOS : les pipelines précédents et les contrôles D7 sont disponibles ; **pas de recette réelle exécutée dans ce bloc**.

## Anomalie reproductible dans le code (corrigée)
**P194-TV-001 — collision de séries homonymes entre catégories M3U.**

Avant : `seriesMap` était indexée uniquement par le nom normalisé de la série, alors que l'identifiant public variait avec la catégorie. Deux entrées `Une série S01E02` (FR - Séries) et `Une série S02E03` (EN - Series) pouvaient être agrégées dans une seule série. Cela mélangeait les épisodes et rendait la deuxième catégorie inaccessible comme série distincte.

Après : clé de regroupement composée du titre normalisé **et** de la catégorie normalisée. Les deux séries restent distinctes, chacune conserve exclusivement ses épisodes réellement présents. Aucun épisode fictif ajouté.

Test automatisé de non-régression `tests/m3u-functional-audit-194.cjs`, sur les deux adaptateurs Tizen/webOS : Live TV, VOD, série par catégorie, saisons et épisodes déclarés. Exécution intégrée aux deux CI TV.

## Points non démontrés par cet audit
- Tests de lecture sur appareils : codecs, HLS/TS, DRM, reprises réelles, audio et sous-titres.
- Services Supabase, comptes/profils et persistance interappareils en conditions de production.
- Android/iOS : parité fonctionnelle M3U/Xtream pour Live/VOD/Series avec fournisseurs d'essai autorisés.
- Capture visuelle comparative D6/D7 sur téléphones, tablettes et TV.

## Priorités ensuite
1. #195 — scénarios de catalogues M3U et Xtream sur **toutes** les plateformes, tests automatisés et tests sur appareils.
2. #196 — médias vidéo sur appareils, diagnostics format/codecs/pistes, reprise.
3. #197 — profils, playlists multiples et synchronisation.
4. D7 — conserver `NON EXÉCUTÉ` tant qu'aucune preuve réelle n'est collectée.

**Conclusion :** cet audit fournit une correction vérifiable et des tests supplémentaires ; il ne certifie pas à lui seul l'application complète.
