# Aviator Tracker

Application Android de suivi OCR local d’un multiplicateur Aviator. L’application utilise `MediaProjection` avec consentement Android, analyse uniquement la zone sélectionnée en mémoire environ deux fois par seconde et applique une machine d’états :

`WAITING → FLIGHT → COUNTDOWN_CANDIDATE → WAITING`

Une valeur n’est enregistrée que si un multiplicateur fiable a été vu puis que le multiplicateur disparaît et qu’un compte à rebours numérique est détecté dans au moins deux captures successives. Les nombres sans suffixe `x` ne sont jamais enregistrés comme multiplicateurs.

## Limites réelles

L’OCR peut échouer avec une image floue, une petite police, une compression, un thème différent ou un recadrage incorrect. La capture peut aussi être bloquée par l’application cible ou limitée par le fabricant Android. Le résultat affiché est donc une observation OCR, pas une garantie ni une prédiction.

## Build

Le workflow `.github/workflows/build-apk.yml` produit `app-debug.apk` comme artefact GitHub Actions.
