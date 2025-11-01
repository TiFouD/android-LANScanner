# LAN Scanner pour Android

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4.svg?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)
[![Ktor](https://img.shields.io/badge/Ktor-Networking-007FFF.svg?style=flat-square)](https://ktor.io/)
[![Coroutines](https://img.shields.io/badge/Kotlin-Coroutines-orange.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/docs/coroutines-overview.html)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat-square)](LICENSE)

**LAN Scanner** est une application Android native conçue pour découvrir et afficher les appareils connectés à votre réseau local (WiFi).

Ce projet a été développé dans le but de démontrer la mise en œuvre d'une application Android moderne, en se concentrant sur les points suivants :
* Interaction avec une API réseau locale (Freebox API).
* Utilisation des bibliothèques Jetpack modernes (Compose, ViewModel).
* Gestion avancée de l'asynchronisme avec les Coroutines Kotlin.
* Intégration d'une pile réseau moderne (Ktor + Kotlinx Serialization).

*(Note : L'application est actuellement optimisée pour les réseaux équipés d'une Freebox, voir la Roadmap pour l'implémentation d'un scanner générique).*

---

## Fonctionnalités Actuelles

* **Découverte de la Freebox** : Utilise la découverte de services réseau (NSD) pour localiser automatiquement l'API de la Freebox sur le réseau (`_fbx-api._tcp`).
* **Authentification Sécurisée** : Gère le flux d'autorisation complet avec l'API Freebox, incluant la demande de permission et le suivi de l'approbation sur le boîtier.
* **Scan Détaillé des Appareils** : Récupère la liste complète des appareils connectés en temps réel depuis l'API du routeur.
* **Informations Complètes** : Affiche le **nom** (hostname), l'**adresse IP** locale et l'**adresse MAC** de chaque appareil.
* **Identification du Fabricant** : Utilise l'adresse MAC (via son préfixe OUI) pour identifier le fabricant de l'appareil (Apple, Google, Samsung, etc.) et afficher une icône correspondante.

---

## Pile Technique (Tech Stack)

* **UI** : 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) pour une interface utilisateur déclarative et moderne.
* **Architecture** : MVVM (Model-View-ViewModel).
* **Asynchronisme** : [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) pour toutes les opérations réseau et asynchrones (`viewModelScope`, `Dispatchers.IO`).
* **Réseau** : [Ktor Client](https://ktor.io/docs/client-overview.html) pour les appels HTTP à l'API Freebox.
* **Parsing JSON** : [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) pour la sérialisation/désérialisation des requêtes et réponses de l'API.
* **Dépendances** : `gradle/libs.versions.toml` pour une gestion centralisée des versions.

---

## 🚧 Roadmap & Work in Progress 🚧

Ce projet est en développement actif. Voici les prochaines étapes prévues pour améliorer l'application :

### 1. Scanner Réseau Générique
* **Objectif** : Rendre l'application compatible avec *tous* les routeurs, et pas seulement les Freebox.
* **Implémentation** : Activer le `NetworkScanner.kt` comme solution de repli ("fallback"). Si aucune Freebox n'est détectée, l'application lancera un scan "brute force" du sous-réseau (ping de 192.168.x.1 à 254) pour trouver les hôtes actifs et résoudre leur hostname.
* **Note** : Ce mode générique ne pourra pas récupérer les adresses MAC, en raison des restrictions de sécurité d'Android.

### 2. Fonctionnalités "Power-User"
* **Objectif** : Ajouter des outils réseau avancés pour les appareils découverts.
* **Implémentation** :
    * **Scan de Ports** : Permettre de sélectionner un appareil pour lancer un scan des ports TCP courants (ex: 22, 80, 443, 8080) afin d'identifier les services ouverts.
    * **Wake-on-LAN (WoL)** : Ajouter un bouton "Réveiller" qui enverra un "Magic Packet" UDP à l'adresse MAC de l'appareil (disponible via l'API Freebox) pour le sortir de veille.

### 3. Architecture de Persistance
* **Objectif** : Créer un historique des appareils et suivre les changements sur le réseau.
* **Implémentation** :
    * Intégrer la bibliothèque **Room** pour créer une base de données locale.
    * Sauvegarder chaque appareil scanné avec un `timestamp`.
    * Afficher un statut "Nouveau" ou "Hors ligne" pour les appareils en comparant les scans actuels avec l'historique en base.
    * Utiliser **Kotlin Flows** pour exposer les données de Room à l'UI de manière réactive.

### 4. Améliorations UI/UX
* **Objectif** : Moderniser l'UI et optimiser la gestion des ressources.
* **Implémentation** :
    * Remplacer les `ListItem` par des `Card` Material3 pour une meilleure hiérarchie visuelle.
    * Externaliser la map de correspondance OUI (fabricant) du `DeviceIconMapper.kt` vers un fichier `vendors.json` dans les *assets* de l'application.

---

## Installation

1.  Clonez ce dépôt :
    ```sh
    git clone [https://github.com/](https://github.com/)[VOTRE-NOM-UTILISATEUR]/[NOM-DU-REPO].git
    ```
2.  Ouvrez le projet avec la dernière version stable d'Android Studio.
3.  Connectez un appareil Android en mode débogage USB (l'émulateur ne fonctionnera pas car il n'est pas sur le même réseau WiFi que votre Freebox).
4.  Compilez et exécutez l'application.