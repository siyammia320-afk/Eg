package com.example.data

import java.util.Calendar

data class GeneratedAccountProfile(
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val sexCode: String, // "1" for Female, "2" for Male
    val day: String,
    val month: String,
    val year: String
)

object NameGenerator {

    // ================= BANGLA NAMES =================
    private val FEMALE_BANGLA_FIRST = listOf(
        "মারিয়া", "আয়েশা", "নুসরাত", "সামিয়া", "সাদিয়া", "তানজিনা", "রাবেয়া", "খাদিজা",
        "সুলতানা", "ফাতিমা", "জান্নাতুল", "ফারজানা", "সুমাইয়া", "তাসনিয়া", "রিয়া", "শরিফা",
        "তাছলিমা", "রোকসানা", "মেহজাবিন", "মিম", "আফরিন", "সাবরিনা", "তানিয়া", "তামান্না",
        "ইসরাত", "সুরাইয়া", "লিজা", "সানজিদা", "নাদিয়া", "আফসানা", "তাবাসসুম", "মুনিরা",
        "সাবিনা", "রুমানা", "জেরিন", "নাসরিন", "ফরিদা", "মাহমুদা", "শারমিন", "নিশাত",
        "ফারিয়া", "তাসনিম", "শায়লা", "রুপা", "তাহমিনা", "জান্নাত", "সাবিকুন", "লামিয়া",
        "আলেয়া", "শিউলি", "ঝরনা", "রেহানা", "আসমাত", "শাহিনুর", "সুলতানা", "রোকেয়া",
        "নাজমা", "লুবনা", "রিতু", "পপি", "মৌসুমি", "শাকিলা", "মনিরা", "হালিমা"
    )

    private val FEMALE_BANGLA_LAST = listOf(
        "আক্তার", "খাতুন", "জাহান", "ইসলাম", "সুলতানা", "রহমান", "চৌধুরী", "বেগম",
        "পারভীন", "শেখ", "খানম", "নেসা", "আহমেদ", "হোসেন", "মিয়া", "তালুকদার", "সরকার", "হাসান",
        "মোল্লা", "মন্ডল", "শিকদার", "মুন্সী"
    )

    private val MALE_BANGLA_FIRST = listOf(
        "রাহিম", "তানজিল", "সাব্বির", "আরিফ", "মেহেদী", "শাকিল", "মোহাম্মদ", "রেজোয়ান",
        "তামিম", "সাকিব", "মাহমুদুল", "ইমরান", "আল আমিন", "আশরাফুল", "রাফি", "জাহিদ",
        "নাজমুল", "কামরুল", "সুমন", "রাকিব", "সিয়াম", "ফাহিম", "হাসিব", "তৌহিদ",
        "কায়সার", "মাহফুজ", "নাঈম", "ফয়সাল", "রুবেল", "সোহেল", "রিফাত", "আকাশ",
        "শাহিন", "তারেক", "জুবায়ের", "হাসান", "নাসিম", "সাইফুল", "সাইফ", "সোহাগ",
        "মনোয়ার", "শামীম", "বাপ্পি", "লিটন", "মাসুদ", "রাজু", "আমিনুল", "রেজাউল", "বুলবুল",
        "আদনান", "রায়হান", "মিজান", "কাওসার", "আসিফ", "জুবায়ের", "শিপন", "শফিক"
    )

    private val MALE_BANGLA_LAST = listOf(
        "তালুকদার", "আহমেদ", "হোসেন", "খান", "হাসান", "চৌধুরী", "রহমান", "ইসলাম",
        "মিয়া", "শেখ", "প্রধান", "সরকার", "আলী", "কাজী", "মোল্লা", "বেপারী", "মুন্সী", "ইকবাল",
        "পাটোয়ারী", "ভুঁইয়া", "হাওলাদার", "মজুমদার"
    )

    // ================= ENGLISH / GLOBAL NAMES =================
    private val FEMALE_ENGLISH_FIRST = listOf(
        "Mariya", "Ayesha", "Nusrat", "Samia", "Sadia", "Tanjina", "Rabeya", "Khadija",
        "Sultana", "Fatima", "Jannatul", "Farzana", "Sumaiya", "Tasnia", "Riya", "Sharifa",
        "Taslima", "Roksana", "Mehzabine", "Mim", "Afrin", "Sabrina", "Tania", "Tamanna",
        "Israt", "Suraiya", "Liza", "Sanjida", "Nadia", "Afsana", "Tabassum", "Munira",
        "Sabina", "Rumana", "Zerin", "Nasrin", "Farida", "Mahmuda", "Sharmin", "Nishat",
        "Faria", "Tasnim", "Shayla", "Rupa", "Tahmina", "Jannat", "Sabikun", "Lamia",
        "Olivia", "Emma", "Charlotte", "Amelia", "Sophia", "Isabella", "Mia", "Evelyn",
        "Harper", "Camila", "Gianna", "Abigail", "Luna", "Ella", "Elizabeth", "Sofia",
        "Emily", "Avery", "Mila", "Scarlett", "Eleanor", "Madison", "Layla", "Penelope"
    )

    private val FEMALE_ENGLISH_LAST = listOf(
        "Akter", "Khatun", "Jahan", "Islam", "Sultana", "Rahman", "Chowdhury", "Begum",
        "Parvin", "Sheikh", "Khanam", "Nesa", "Ahmed", "Hossain", "Mia", "Talukder", "Sarkar", "Hasan",
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
        "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"
    )

    private val MALE_ENGLISH_FIRST = listOf(
        "Rahim", "Tanzil", "Sabbir", "Arif", "Mehedi", "Shakil", "Mohammad", "Rezwan",
        "Tamim", "Sakib", "Mahmudul", "Imran", "Al Amin", "Ashraful", "Rafi", "Zahid",
        "Nazmul", "Kamrul", "Suman", "Rakib", "Siyam", "Fahim", "Hasib", "Towhid",
        "Kaysar", "Mahfuz", "Nayeem", "Foysal", "Rubel", "Sohel", "Rifat", "Akash",
        "Shahin", "Tarek", "Zubair", "Hasan", "Nasim", "Saiful", "Saif", "Sohag",
        "Liam", "Noah", "Oliver", "James", "Elijah", "William", "Henry", "Lucas",
        "Benjamin", "Theodore", "Mateo", "Levi", "Sebastian", "Daniel", "Jack", "Michael",
        "Alexander", "Owen", "Asher", "Samuel", "Ethan", "Leo", "Jackson", "Mason", "Ezra"
    )

    private val MALE_ENGLISH_LAST = listOf(
        "Talukder", "Ahmed", "Hossain", "Khan", "Hasan", "Chowdhury", "Rahman", "Islam",
        "Mia", "Sheikh", "Prodhan", "Sarkar", "Ali", "Kazi", "Molla", "Bepari", "Munshi", "Iqbal",
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson", "Anderson", "Taylor",
        "Thomas", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark"
    )

    // ================= FRENCH NAMES =================
    private val FEMALE_FRENCH_FIRST = listOf(
        "Emma", "Chloé", "Inès", "Camille", "Léa", "Manon", "Sarah", "Juliette",
        "Lucie", "Zoé", "Océane", "Charlotte", "Clara", "Émilie", "Anaïs", "Alice",
        "Mathilde", "Laura", "Pauline", "Marine", "Marie", "Sophie", "Louise", "Jade",
        "Mila", "Lola", "Ambre", "Rose", "Agathe", "Elena", "Victoire", "Margaux",
        "Romane", "Léna", "Eva", "Éléonore", "Adèle", "Roxane", "Salomé", "Célia"
    )

    private val MALE_FRENCH_FIRST = listOf(
        "Jean", "Pierre", "Lucas", "Louis", "Hugo", "Thomas", "Gabriel", "Arthur",
        "Jules", "Maxime", "Alexandre", "Antoine", "Nicolas", "Julien", "Romain", "Paul",
        "Clément", "Florian", "Théo", "Nathan", "Léo", "Raphaël", "Valentin", "Mathis",
        "Noah", "Adam", "Tom", "Sacha", "Maxence", "Timéo", "Robin", "Gabin", "Augustin"
    )

    private val FRENCH_LAST = listOf(
        "Dupont", "Martin", "Durand", "Lefèvre", "Moreau", "Petit", "Roux", "Richard",
        "Simon", "Laurent", "Michel", "Garcia", "Thomas", "Robert", "David", "Bertrand",
        "Dubois", "Lambert", "Bonnet", "François", "Martinez", "Legrand", "Garnier", "Faure",
        "Rousseau", "Blanc", "Guerin", "Muller", "Henry", "Roussel", "Nicolas", "Perrin",
        "Morin", "Mathieu", "Clément", "Gauthier", "Dumont", "Lopez", "Fontaine", "Chevalier"
    )

    // ================= ARABIC NAMES =================
    private val FEMALE_ARABIC_FIRST = listOf(
        "Fatima", "Amina", "Zainab", "Mariam", "Noor", "Layla", "Salma", "Huda",
        "Rania", "Yasmin", "Farida", "Samira", "Malak", "Lina", "Reem", "Dina",
        "Nour", "Jana", "Nadine", "Asma", "Hana", "Maya", "Tala", "Soraya"
    )

    private val MALE_ARABIC_FIRST = listOf(
        "Omar", "Ali", "Youssef", "Ibrahim", "Tariq", "Zaid", "Hamza", "Bilal",
        "Mustafa", "Karim", "Walid", "Sami", "Khaled", "Hassan", "Hussein", "Nabil",
        "Fahad", "Adel", "Faris", "Amir", "Marwan", "Ziyad", "Anas", "Rashid"
    )

    private val ARABIC_LAST = listOf(
        "Al-Mansoor", "Al-Hashimi", "Haddad", "Najjar", "Khoury", "Al-Sayed", "Mahmoud",
        "Suleiman", "Al-Qasimi", "Farhat", "Darwish", "Saleh", "Al-Ghamdi", "Al-Otaibi",
        "Qasim", "Bousaid", "Mansour", "Ghanem", "Shaker", "Barakat", "Zahran", "Abboud"
    )

    fun generateProfile(genderConfig: String, langConfig: String, ageConfig: String): GeneratedAccountProfile {
        // Determine Gender: FEMALE, MALE, or RANDOM
        val isFemale = when (genderConfig.uppercase().trim()) {
            "FEMALE" -> true
            "MALE" -> false
            else -> (0..1).random() == 0 // RANDOM / MIXED
        }

        val lang = langConfig.uppercase().trim()

        val firstName: String
        val lastName: String

        when {
            lang == "FRENCH" || lang == "FR" -> {
                firstName = if (isFemale) FEMALE_FRENCH_FIRST.random() else MALE_FRENCH_FIRST.random()
                lastName = FRENCH_LAST.random()
            }
            lang == "ARABIC" || lang == "AR" -> {
                firstName = if (isFemale) FEMALE_ARABIC_FIRST.random() else MALE_ARABIC_FIRST.random()
                lastName = ARABIC_LAST.random()
            }
            lang == "BANGLA" || lang == "BN" -> {
                firstName = if (isFemale) FEMALE_BANGLA_FIRST.random() else MALE_BANGLA_FIRST.random()
                lastName = if (isFemale) FEMALE_BANGLA_LAST.random() else MALE_BANGLA_LAST.random()
            }
            else -> { // DEFAULT: ENGLISH / GLOBAL
                firstName = if (isFemale) FEMALE_ENGLISH_FIRST.random() else MALE_ENGLISH_FIRST.random()
                lastName = if (isFemale) FEMALE_ENGLISH_LAST.random() else MALE_ENGLISH_LAST.random()
            }
        }

        val sexCode = if (isFemale) "1" else "2"

        // Age calculation
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val targetAge = when (ageConfig) {
            "21+" -> (21..35).random()
            "18+" -> (18..35).random()
            else -> (18..40).random()
        }

        val birthYear = (currentYear - targetAge).toString()
        val birthMonth = (1..12).random().toString()
        val birthDay = (1..28).random().toString()

        return GeneratedAccountProfile(
            firstName = firstName,
            lastName = lastName,
            fullName = "$firstName $lastName",
            sexCode = sexCode,
            day = birthDay,
            month = birthMonth,
            year = birthYear
        )
    }
}
