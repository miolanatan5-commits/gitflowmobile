# GitFlow Mobile

An Android app (Kotlin + Jetpack Compose) that makes GitHub easier to use from your phone, built with accessibility (TalkBack) in mind.

## Features
- Sign in with a personal access token (stored encrypted on the device)
- List, create and delete repositories (deleting requires typing the repository name)
- Search all of GitHub: repositories, users/organizations and code
- Download APKs published in the releases of any repository, and install them
- Upload a .zip as a single commit (with an option to strip the root folder)
- Build the APK on GitHub Actions and save it to Downloads (with the full log if the build fails)

## Token
Create a *classic* token with the `repo`, `workflow` and `delete_repo` scopes.

## Localization
All UI text lives in `app/src/main/res/values*/strings.xml`.
English is the default (`values/`); Brazilian Portuguese is in `values-pt-rBR/`.
To add a language, copy `values/strings.xml` to `values-<code>/strings.xml` and translate it.

## Structure
- `app/src/main/java/com/natam/gitflowmobile/data` – GitHub API client, zip uploader, build service, secure token store
- `app/src/main/java/com/natam/gitflowmobile/ui` – ViewModel, shared components and screens
