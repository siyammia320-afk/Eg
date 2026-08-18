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

    private val FEMALE_BANGLA_FIRST = listOf(
        "মারিয়া", "আয়েশা", "নুসরাত", "সামিয়া", "সাদিয়া", "তানজিনা", "রাবেয়া", "খাদিজা",
        "সুলতানা", "ফাতিমা", "জান্নাতুল", "ফারজানা", "সুমাইয়া", "তাসনিয়া", "রিয়া", "শরিফা",
        "তাছলিমা", "রোকসানা", "মেহজাবিন", "মিম", "আফরিন", "সাবরিনা", "তানিয়া", "তামান্না",
        "ইসরাত", "সুরাইয়া", "লিজা", "সানজিদা", "নাদিয়া", "আফসানা", "তাবাসসুম", "মুনিরা",
        "সাবিনা", "রুমানা", "জেরিন", "নাসরিন", "ফরিদা", "মাহমুদা", "শারমিন", "নিশাত",
        "ফারিয়া", "তাসনিম", "শায়লা", "রুপা", "তাহমিনা", "জান্নাত", "সাবিকুন", "লামিয়া",
        "আলেয়া", "শিউলি", "ঝরনা", "রেহানা", "আসমাত", "শাহিনুর", "সুলতানা", "রোকেয়া"
    )

    private val FEMALE_BANGLA_LAST = listOf(
        "আক্তার", "খাতুন", "জাহান", "ইসলাম", "সুলতানা", "রহমান", "চৌধুরী", "বেগম",
        "পারভীন", "শেখ", "খানম", "নেসা", "আহমেদ", "হোসেন", "মিয়া", "তালুকদার", "সরকার", "হাসান"
    )

    private val FEMALE_ENGLISH_FIRST = listOf(
        "Mariya", "Ayesha", "Nusrat", "Samia", "Sadia", "Tanjina", "Rabeya", "Khadija",
        "Sultana", "Fatima", "Jannatul", "Farzana", "Sumaiya", "Tasnia", "Riya", "Sharifa",
        "Taslima", "Roksana", "Mehzabine", "Mim", "Afrin", "Sabrina", "Tania", "Tamanna",
        "Israt", "Suraiya", "Liza", "Sanjida", "Nadia", "Afsana", "Tabassum", "Munira",
        "Sabina", "Rumana", "Zerin", "Nasrin", "Farida", "Mahmuda", "Sharmin", "Nishat",
        "Faria", "Tasnim", "Shayla", "Rupa", "Tahmina", "Jannat", "Sabikun", "Lamia",
        "Aleya", "Rehana", "Shahinur", "Rokeya", "Tahsina", "Anika", "Mousumi"
    )

    private val FEMALE_ENGLISH_LAST = listOf(
        "Akter", "Khatun", "Jahan", "Islam", "Sultana", "Rahman", "Chowdhury", "Begum",
        "Parvin", "Sheikh", "Khanam", "Nesa", "Ahmed", "Hossain", "Mia", "Talukder", "Sarkar", "Hasan"
    )

    private val MALE_BANGLA_FIRST = listOf(
        "রাহিম", "তানজিল", "সাব্বির", "আরিফ", "মেহেদী", "শাকিল", "মোহাম্মদ", "রেজোয়ান",
        "তামিম", "সাকিব", "মাহমুদুল", "ইমরান", "আল আমিন", "আশরাফুল", "রাফি", "জাহিদ",
        "নাজমুল", "কামরুল", "সুমন", "রাকিব", "সিয়াম", "ফাহিম", "হাসিব", "তৌহিদ",
        "কায়সার", "মাহফুজ", "নাঈম", "ফয়সাল", "রুবেল", "সোহেল", "রিফাত", "আকাশ",
        "শাহিন", "তারেক", "জুবায়ের", "হাসান", "নাসিম", "সাইফুল", "সাইফ", "সোহাগ",
        "মনোয়ার", "শামীম", "বাপ্পি", "লিটন", "মাসুদ", "রাজু", "আমিনুল", "রেজাউল", "বুলবুল"
    )

    private val MALE_BANGLA_LAST = listOf(
        "তালুকদার", "আহমেদ", "হোসেন", "খান", "হাসান", "চৌধুরী", "রহমান", "ইসলাম",
        "মিয়া", "শেখ", "প্রধান", "সরকার", "আলী", "কাজী", "মোল্লা", "বেপারী", "মুন্সী", "ইকবাল"
    )

    private val MALE_ENGLISH_FIRST = listOf(
        "Rahim", "Tanzil", "Sabbir", "Arif", "Mehedi", "Shakil", "Mohammad", "Rezwan",
        "Tamim", "Sakib", "Mahmudul", "Imran", "Al Amin", "Ashraful", "Rafi", "Zahid",
        "Nazmul", "Kamrul", "Suman", "Rakib", "Siyam", "Fahim", "Hasib", "Towhid",
        "Kaysar", "Mahfuz", "Nayeem", "Foysal", "Rubel", "Sohel", "Rifat", "Akash",
        "Shahin", "Tarek", "Zubair", "Hasan", "Nasim", "Saiful", "Saif", "Sohag",
        "Monowar", "Shamim", "Bappi", "Liton", "Masud", "Raju", "Aminul", "Rezaul", "Bulbul"
    )

    private val MALE_ENGLISH_LAST = listOf(
        "Talukder", "Ahmed", "Hossain", "Khan", "Hasan", "Chowdhury", "Rahman", "Islam",
        "Mia", "Sheikh", "Prodhan", "Sarkar", "Ali", "Kazi", "Molla", "Bepari", "Munshi", "Iqbal"
    )

    fun generateProfile(genderConfig: String, langConfig: String, ageConfig: String): GeneratedAccountProfile {
        // Determine Gender
        val isFemale = when (genderConfig.uppercase()) {
            "FEMALE" -> true
            "MALE" -> false
            else -> (0..1).random() == 0 // RANDOM / MIXED
        }

        val isBangla = langConfig.uppercase() == "BANGLA"

        val firstName: String
        val lastName: String

        if (isFemale) {
            if (isBangla) {
                firstName = FEMALE_BANGLA_FIRST.random()
                lastName = FEMALE_BANGLA_LAST.random()
            } else {
                firstName = FEMALE_ENGLISH_FIRST.random()
                lastName = FEMALE_ENGLISH_LAST.random()
            }
        } else {
            if (isBangla) {
                firstName = MALE_BANGLA_FIRST.random()
                lastName = MALE_BANGLA_LAST.random()
            } else {
                firstName = MALE_ENGLISH_FIRST.random()
                lastName = MALE_ENGLISH_LAST.random()
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
