# Feuille de Route Complète : Scanner de Réseau WiFi (Android/Kotlin)

## Objectif Principal

Créer une application Android native en Kotlin qui scanne le réseau WiFi local pour découvrir les appareils connectés, récupérer leur adresse IP, leur hostname, et (si possible) identifier le type d'appareil.

## Vos Atouts (sur lesquels capitaliser)

- **Java :** La syntaxe de Kotlin vous sera très familière (c'est du Java "moderne").
- **Réseau :** Vous comprenez ce qu'est un sous-réseau, un ping, un port, un hostname, et une adresse MAC. C'est 90% de la complexité logique du projet.

## Phase 0 : L'Installation (Le "Jour 0")

Avant d'écrire une ligne de Kotlin, il faut l'environnement.

- **Télécharger Android Studio :** C'est l'IDE officiel de Google. Prenez la dernière version stable (ex: "Jellyfish" ou plus récent).
- **Installation :** L'installeur vous guidera pour télécharger le **Android SDK** (les outils pour "compiler" l'application) et les images d'émulateur.
- **Configurer l'Émulateur :**
  - Dans Android Studio, allez dans Tools > Device Manager.
  - Créez un "Virtual Device" (ex: un Pixel 8).
  - **Important :** L'émulateur est sur son propre sous-réseau virtuel. Pour scanner _votre_ WiFi, il sera **1000 fois plus simple de tester sur un vrai téléphone Android**.
- **Activer le "Mode Développeur" sur votre téléphone :**
  - Allez dans Paramètres > À propos du téléphone.
  - Tapotez 7 fois sur "Numéro de build".
  - Un nouveau menu "Options pour les développeurs" apparaît. Activez "Débogage USB".
- **Connecter votre téléphone :** Branchez votre téléphone à votre PC via USB. Android Studio le détectera automatiquement comme cible de déploiement.

## Phase 1 : Le Projet "Coquille Vide" (L'UI)

Nous allons créer un projet avec un bouton "Scanner" et une zone de texte pour afficher les résultats. Nous utilisons **Jetpack Compose**, le framework d'UI moderne (très similaire à React si vous connaissez).

- **Créer le Projet :**
  - File > New > New Project...
  - Choisissez le template **"Empty Activity"** (les templates récents utilisent Compose par défaut).
  - Langage : Kotlin.
  - Minimum SDK : API 26 (Oreo) est un bon choix par défaut.
- **Comprendre le Fichier MainActivity.kt :**
  - C'est l'écran principal de votre application.
  - La fonction @Composable est une fonction qui "dessine" un morceau d'interface (comme un composant React).
  - La fonction setContent { ... } est le point d'entrée de votre UI.
- **Ajouter les Permissions (Crucial) :**
  - Votre application doit "demander la permission" d'accéder au réseau.
  - Ouvrez le fichier app/src/main/AndroidManifest.xml.
  - Ajoutez ces lignes juste avant la balise &lt;application&gt; (voir le fichier que je vous génère).
- **Construire l'UI de base :**
  - Nous avons besoin d'un état pour stocker la liste des appareils trouvés.  
        // "remember" garde la variable en mémoire, "mutableStateOf" la rend "observable"  
        // Quand cette liste changera, l'UI se mettra à jour.  
        var discoveredDevices by remember { mutableStateOf(listOf&lt;String&gt;()) }  

  - Nous avons besoin d'un Button pour lancer le scan.
  - Nous avons besoin d'une LazyColumn (une liste optimisée) pour afficher les résultats.
  - (Je vous fournis ce code de base dans le fichier MainActivity.kt).

## Phase 2 : La Logique Réseau (Le Cœur)

C'est là que vos connaissances réseau entrent en jeu.

- **Le Piège Mortel : Le Thread Principal (Main Thread)**
  - Android est strict : **toute opération longue (réseau, disque) est INTERDITE sur le thread principal (UI)**. Si vous le faites, votre application plantera (erreur NetworkOnMainThreadException).
  - **Solution :** Les **Coroutines Kotlin**. C'est la gestion moderne de l'asynchronisme (l'équivalent des async/await en JS ou des Threads en Java, mais en beaucoup plus simple).
- **Créer une classe NetworkScanner :**
  - Pour garder le code propre, n'écrivez pas la logique réseau dans MainActivity.kt.
  - Créez un nouveau fichier NetworkScanner.kt (File > New > Kotlin Class/File).
- **Étape 2a : Trouver le sous-réseau à scanner**
  - L'application doit connaître sa propre IP pour deviner le sous-réseau (ex: si mon tel est 192.168.1.50, je dois scanner 192.168.1.1 à 192.168.1.254).
  - Vous aurez besoin du ConnectivityManager (le service système d'Android) pour obtenir l'adresse IP de l'appareil.
- **Étape 2b : La boucle de scan (L'approche "brute force")**
  - La méthode la plus simple est de "pinger" chaque adresse IP du sous-réseau.
  - **N'utilisez PAS InetAddress.isReachable() !** C'est la méthode Java standard, mais elle est très peu fiable sur Android (elle utilise ICMP, souvent bloqué, ou un port TCP (port 7) jamais ouvert).
  - **La Vraie Méthode :** Tentez d'ouvrir un Socket sur un port commun (ex: 80, 135, 443) avec un _timeout_ très court (ex: 50-100ms). Si le socket se connecte ou est _refusé_ (Connection Refused), l'hôte est "vivant". S'il _timeout_, l'hôte est "mort".
- **Étape 2c : Paralléliser avec les Coroutines**
  - Scanner 254 adresses une par une prendra 2 minutes. C'est trop long.
  - Vous devez lancer les 254 scans en parallèle.
  - Avec les Coroutines, c'est incroyablement simple. Vous allez "lancer" 254 "jobs" dans un pool de threads (Dispatchers.IO) et attendre qu'ils soient tous finis.
  - **Exemple de logique pour un seul scan :**  
        // Ceci doit être appelé depuis une Coroutine  
        suspend fun scanIp(ip: String): DeviceInfo? { // DeviceInfo est une data class  
        return withContext(Dispatchers.IO) { // Change de thread  
        try {  
        val socket = Socket()  
        // Tente de se connecter au port 135 (souvent ouvert sur Windows)  
        // avec un timeout de 50ms  
        socket.connect(InetSocketAddress(ip, 135), 50)  
        socket.close()  
        // Si on arrive ici, l'hôte est vivant  
        val hostname = InetAddress.getByName(ip).hostName  
        return@withContext DeviceInfo(ip, hostname)  
        } catch (e: Exception) {  
        // Timeout ou Refus = hôte mort ou port fermé, on ignore  
        return@withContext null  
        }  
        }  
        }  

## Phase 3 : L'Alternative (L'API Freebox)

L'approche "brute force" (Phase 2) est universelle, mais limitée (on ne trouve que les hôtes qui répondent). Vous avez mentionné "ma freebox". C'est un indice clé !

**Votre Freebox a une API !** Elle sait _exactement_ qui est connecté.

- **Documentation :** Cherchez "API Freebox OS" (ou "Freebox SDK").
- **Principe :**
  - L'application doit d'abord s'authentifier auprès de la Freebox (<https://www.google.com/search?q=http://mafreebox.freebox.fr/>).
  - Une fois authentifiée (l'utilisateur devra appuyer sur le bouton de la box), l'application reçoit un token.
  - Avec ce token, vous pouvez appeler un "endpoint" (une URL) qui vous renvoie un fichier **JSON** avec la liste _complète_ des appareils, leur nom, leur adresse IP, et leur **ADRESSE MAC**.
- **C'est la méthode "Pro" :** C'est 100% fiable, instantané, et ça ne vide pas la batterie. En revanche, votre application ne fonctionnera _que_ pour les utilisateurs de Freebox.

## Phase 4 : Identification de l'Appareil

C'est votre "si possible". C'est la partie la plus dure.

- **Si vous utilisez l'API Freebox (Phase 3) :** Vous avez l'adresse MAC. L'adresse MAC contient l'**OUI (Organizationally Unique Identifier)**. Les 6 premiers caractères (AA:BB:CC:xx:xx:xx) identifient le fabricant.
  - Vous pouvez embarquer dans votre application une base de données "OUI-to-Vendor" (il en existe des gratuites).
  - Si la MAC commence par 9C:20:7B, la base vous dira "Apple, Inc.". Vous pouvez alors afficher une icône .
- **Si vous utilisez le scan "brute force" (Phase 2) :**
  - **C'est le mur.** Depuis Android 10, **une application ne peut plus lire la table ARP du système.** Il est impossible pour une application normale d'obtenir l'adresse MAC d'un _autre_ appareil sur le réseau.
  - Votre seule piste est le hostname (ex: PC-DE-PAUL, android-123456.home). C'est souvent suffisant pour deviner.

## Mon Conseil Stratégique

- Commencez par la **Phase 0 et 1**. Ayez une app avec un bouton et une liste vide.
- Implémentez la **Phase 2** (scan brute force). Vous aurez la satisfaction de voir les IP et les hostnames apparaître. C'est un super défi d'apprentissage sur les Coroutines.
- _Ensuite_, si vous voulez une app vraiment puissante (pour vous), attaquez la **Phase 3** (API Freebox). Vous apprendrez à faire des appels réseau (avec des librairies comme **Retrofit** ou **Ktor**) et à analyser du JSON (avec **Kotlinx Serialization**).
- La **Phase 4** (identification par MAC) n'est possible que si vous réussissez la Phase 3.

Vous avez un projet passionnant devant vous. Prévoyez du temps, ne vous découragez pas face aux erreurs (il y en aura !), et amusez-vous bien.

Voici les fichiers de départ pour vous lancer.
