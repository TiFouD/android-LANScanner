# LAN Scanner pour Android

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4.svg?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)
[![Ktor](https://img.shields.io/badge/Ktor-Networking-007FFF.svg?style=flat-square)](https://ktor.io/)
[![Coroutines](https://img.shields.io/badge/Kotlin-Coroutines-orange.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/docs/coroutines-overview.html)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat-square)](LICENSE)

**LAN Scanner** est une application Android native conçue pour découvrir et afficher les appareils connectés à votre réseau local (WiFi).

Ce projet a été développé dans le but de démontrer la mise en œuvre d'une application Android moderne, en se concentrant sur les points suivants :
* Interaction avec une API réseau locale (Freebox API).
* Implémentation d'un scanner de sous-réseau générique en solution de repli.
* Utilisation des bibliothèques Jetpack modernes (Compose, ViewModel).
* Gestion avancée de l'asynchronisme avec les Coroutines Kotlin.
* Intégration d'une pile réseau moderne (Ktor + Kotlinx Serialization).

*(Note : L'application est optimisée pour les réseaux équipés d'une Freebox pour récupérer les adresses MAC, mais inclut un scanner générique fonctionnel pour tous les autres réseaux).*

---

## Fonctionnalités Actuelles

* **Découverte de la Freebox** : Utilise la découverte de services réseau (NSD) pour localiser automatiquement l'API de la Freebox sur le réseau (`_fbx-api._tcp`).
* **Authentification Sécurisée (Freebox)** : Gère le flux d'autorisation complet avec l'API Freebox, incluant la demande de permission et le suivi de l'approbation sur le boîtier.
* **Scan Détaillé (via Freebox)** : Récupère la liste complète des appareils connectés en temps réel depuis l'API du routeur.
* **Scan Réseau Générique (Fallback)** : Si aucune Freebox n'est détectée, l'application lance un scan du sous-réseau (via `NetworkScanner.kt`) pour trouver les hôtes actifs et résoudre leur nom d'hôte.
* **Informations Complètes** : Affiche le **nom** (hostname), l'**adresse IP** locale et (si disponible via Freebox) l'**adresse MAC** de chaque appareil.
* **Identification du Fabricant** : Utilise l'adresse MAC (via son préfixe OUI) pour identifier le fabricant de l'appareil (Apple, Google, Samsung, etc.) et afficher une icône correspondante.
* **UI Moderne** : L'interface est bâtie en 100% Jetpack Compose avec Material3, utilisant des `Card` pour une hiérarchie visuelle claire.

---

## Pile Technique (Tech Stack)

* **UI** : 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3) pour une interface utilisateur déclarative.
* **Architecture** : MVVM (Model-View-ViewModel).
* **Asynchronisme** : [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) pour toutes les opérations réseau et asynchrones (`viewModelScope`, `Dispatchers.IO`).
* **Réseau** : [Ktor Client](https://ktor.io/docs/client-overview.html) pour les appels HTTP à l'API Freebox.
* **Parsing JSON** : [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) pour la sérialisation/désérialisation.
* **Dépendances** : `gradle/libs.versions.toml` pour une gestion centralisée des versions.
* **Ressources** : La correspondance OUI (fabricant) est externalisée dans un fichier `lightweight_oui.json` chargé depuis les *assets*.

---

## 🚧 Roadmap & Work in Progress 🚧

Ce projet est en développement actif. Voici les prochaines étapes prévues pour améliorer l'application :

### 1. Fonctionnalités "Power-User"
* **Objectif** : Ajouter des outils réseau avancés pour les appareils découverts.
* **Implémentation** :
    * **Scan de Ports** : Permettre de sélectionner un appareil pour lancer un scan des ports TCP courants (ex: 22, 80, 443, 8080) afin d'identifier les services ouverts.
    * **Wake-on-LAN (WoL)** : Ajouter un bouton "Réveiller" qui enverra un "Magic Packet" UDP à l'adresse MAC de l'appareil (uniquement pour les appareils découverts via l'API Freebox).

### 2. Architecture de Persistance
* **Objectif** : Créer un historique des appareils et suivre les changements sur le réseau.
* **Implémentation** :
    * Intégrer la bibliothèque **Room** pour créer une base de données locale.
    * Sauvegarder chaque appareil scanné avec un `timestamp`.
    * Afficher un statut "Nouveau" ou "Hors ligne" pour les appareils en comparant les scans actuels avec l'historique en base.
    * Utiliser **Kotlin Flows** pour exposer les données de Room à l'UI de manière réactive.

### 3. Amélioration du Scan Générique
* **Objectif** : Augmenter la fiabilité et la vitesse du scan générique.
* **Implémentation** :
    * Le scan actuel (`NetworkScanner.kt`) teste le port 135 avec un timeout court. Explorer des méthodes plus robustes, comme tester *plusieurs* ports courants (ex: 80, 443, 22) en parallèle pour mieux détecter les hôtes qui ne répondent pas sur le 135.
    * Optimiser le `coroutineScope` pour gérer plus efficacement les 254 lancements de jobs.

### 4. Tests Unitaires et d'Intégration
* **Objectif** : Assurer la stabilité et la maintenabilité du code.
* **Implémentation** :
    * Rédiger des tests unitaires pour le `MainViewModel` en mockant les `FreeboxManager` et `NetworkScanner`.
    * Rédiger des tests pour la logique de parsing de `DeviceIconMapper` et la gestion des états dans `FreeboxAuthState`.

### 5. Améliorations UX Avancées
* **Objectif** : Enrichir l'interaction avec la liste des appareils.
* **Implémentation** :
    * Ajouter un "pull-to-refresh" sur la liste pour relancer un scan.
    * Afficher des informations sur l'appareil *actuel* (IP locale, nom du réseau WiFi).
    * Permettre de trier la liste (par IP, par nom) ou d'ajouter un champ de filtre/recherche.

---

## Installation

1.  Clonez ce dépôt.
2.  Ouvrez le projet avec la dernière version stable d'Android Studio.
3.  Connectez un appareil Android en mode débogage USB (l'émulateur ne fonctionnera pas car il n'est pas sur le même réseau WiFi que votre Freebox ou vos appareils LAN).
4.  Compilez et exécutez l'application.