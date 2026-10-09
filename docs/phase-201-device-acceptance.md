# Phase 201 — Recette réelle multiplateforme

> Aucun résultat d'exécution sur appareil réel n'a été fourni pour cette phase. Tous les essais commencent à **NON EXÉCUTÉ**. Une CI verte ne vaut pas validation physique.

## Plateformes
Android téléphone, Android tablette, Android TV/box, iPhone, iPad, Samsung Tizen, LG webOS.

## Jeux de test
Utiliser uniquement des playlists M3U et des identifiants Xtream fournis avec autorisation, contenant chacun au moins une chaîne TV, un film et une série avec un épisode réel. Prévoir aussi un compte Supabase de test, un profil adulte et un profil enfant. Ne jamais consigner les mots de passe, les jetons ou les URL de flux contenant des identifiants.

## Parcours à répéter sur chaque plateforme
- P01 : installation, ouverture, fermeture et relance de l'application
- P02 : connexion, déconnexion et restauration de session
- P03 : sélection du profil et verrouillage PIN parental
- P04 : playlist M3U — télévision, film, série, saison et épisode, sans entrée fictive
- P05 : Xtream Codes — télévision, film, série, saison et épisode, sans entrée fictive
- P06 : ajout, changement, actualisation et suppression de playlist
- P07 : Live TV, zapping, EPG et lecteur
- P08 : film — démarrage, avance, pause, reprise et arrêt
- P09 : épisode — ordre saisons/épisodes, démarrage, reprise
- P10 : favoris, historique et continuer à regarder
- P11 : contrôle parental et changements de profils
- P12 : synchronisation entre deux appareils de test
- P13 : gestion et révocation des appareils
- P14 : panne réseau, erreur fournisseur et reconnexion
- P15 : langues FR/EN et grands caractères si disponibles
- P16 : télécommande/focus et retour sur TV ; gestes et orientation sur mobile/tablette

## Résultat à collecter
Pour chaque combinaison plateforme/parcours : statut parmi `NON EXÉCUTÉ`, `OK`, `ÉCHEC`, `BLOQUÉ`, plus appareil, version OS, SHA du build, date, attendu, observé, capture ou vidéo, et reproduction. Les preuves ne doivent contenir ni identifiant, ni secret de fournisseur.

## Critères de décision
La publication est bloquée si un P0 critique échoue sur une plateforme cible ; un scénario non exécuté reste non validé. Les corrections issues des essais seront apportées dans des PR distinctes avec tests de régression. Les signatures, certificats et inscriptions sur les stores ne sont pas inclus dans cette recette.
