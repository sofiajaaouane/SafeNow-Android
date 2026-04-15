# SafeNow-Android

SafeNow-Android est une application mobile Android inspirée de SafeNow, pensée pour la **sécurité personnelle** et l’**assistance rapide**.

## Aperçu

- **Objectif**: fournir une base d’application orientée “safety” (profil, écrans de gestion, données locales, etc.)
- **Architecture**: MVVM (Model–View–ViewModel) autant que possible
- **Stockage local**: SQLite (via les composants Android usuels)

## Technologies

- **Langage**: Kotlin
- **Plateforme**: Android
- **Build**: Gradle
- **UI**: XML layouts (ex: `app/src/main/res/layout/`)
- **Données**: SQLite (local)

## Prérequis

- **Android Studio** (version récente recommandée)
- **Android SDK** installé via Android Studio
- **JDK** compatible avec Android Studio/Gradle (en général, le JDK embarqué d’Android Studio suffit)

## Installation / Setup

1. **Cloner le projet**

```bash
git clone <votre-repo>
cd SafeNow-Android
```

2. **Ouvrir dans Android Studio**

- Android Studio → **Open** → sélectionner le dossier `SafeNow-Android`

3. **Synchroniser Gradle**

- Au premier lancement, Android Studio propose “**Gradle Sync**”
- Attendre la fin de l’indexation et de la synchronisation

4. **Lancer l’application**

- Brancher un téléphone (débogage USB activé) **ou** créer un émulateur (AVD)
- Cliquer **Run** (▶) et choisir l’appareil

## Connexion à Firebase

Le projet est déjà prêt côté dépendances (Firestore, Realtime Database, FCM). Il manque surtout le fichier `google-services.json` (il est ignoré par git).

1. **Créer/ouvrir un projet Firebase**

- Firebase Console → créer (ou choisir) un projet

2. **Ajouter une application Android dans Firebase**

- “Add app” → Android
- **Android package name**: `com.example.safefnow2` (doit correspondre à `applicationId` dans `app/build.gradle.kts`)
- (Recommandé) Ajouter vos **SHA-1 / SHA-256** si vous utilisez des services qui les demandent (ex: FCM, Auth, etc.)

3. **Télécharger `google-services.json`**

- Télécharger le fichier depuis Firebase Console
- Le placer ici: `app/google-services.json`

4. **Synchroniser Gradle**

- Android Studio → “Sync Now”
- Note: le plugin `com.google.gms.google-services` est appliqué automatiquement **uniquement** si `app/google-services.json` est présent.

5. **Activer les produits Firebase utilisés (si besoin)**

- **Firestore**: activer Cloud Firestore et définir les règles
- **Realtime Database**: activer la base et définir les règles
- **FCM**: activer Cloud Messaging (la réception des notifications dépend aussi de la config Android/serveur)

## Commandes utiles (optionnel)

Depuis la racine du projet:

```bash
gradlew assembleDebug
```

```bash
gradlew test
```

## Structure rapide

- `app/` : module principal Android
- `app/src/main/java/` : code Kotlin
- `app/src/main/res/` : ressources (layouts XML, strings, drawables, etc.)

## Notes

- Les fichiers sous `.gradle/` sont générés par Gradle et ne devraient généralement pas être versionnés.