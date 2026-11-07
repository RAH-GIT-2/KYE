package com.kye.app.data

/**
 * Default India-first categories, subcategories and voice keywords.
 * Merchant keywords outrank generic ones (weight 3 vs 2); category-level keywords have weight 1.
 * Every category also gets an "Others" subcategory when seeded.
 */
data class SeedSub(
    val name: String,
    val keywords: List<String> = emptyList(),
    val merchant: Boolean = false,
    val transfer: Boolean = false
)

data class SeedCategory(
    val name: String,
    val emoji: String,
    val color: Long,
    val subs: List<SeedSub>,
    val keywords: List<String> = emptyList(),
    val kind: CategoryKind = CategoryKind.EXPENSE
)

private fun m(name: String, vararg kw: String) = SeedSub(name, kw.toList(), merchant = true)
private fun g(name: String, vararg kw: String) = SeedSub(name, kw.toList(), merchant = false)

object Seed {
    val categories: List<SeedCategory> = listOf(
        SeedCategory(
            "Food & Dining", "🍛", 0xFFFF7A45,
            listOf(
                m("Swiggy", "swiggy", "swigy", "ஸ்விக்கி"),
                m("Zomato", "zomato", "zomoto", "சொமாட்டோ"),
                m("Domino's", "dominos", "domino", "டாமினோஸ்"),
                m("KFC", "kfc"),
                m("McDonald's", "mcdonalds", "mcdonald", "mcd"),
                m("Pizza Hut", "pizza hut", "pizzahut"),
                g("Hotel / Mess", "hotel", "mess", "saapadu", "sapadu", "meals", "lunch", "dinner", "breakfast",
                    "tiffin", "dosa", "idli", "parotta", "biryani", "briyani", "சாப்பாடு", "ஹோட்டல்", "பிரியாணி"),
                g("Tea & Snacks", "tea", "coffee", "tea kadai", "snacks", "bajji", "vadai", "samosa", "டீ", "காபி"),
                g("Bakery", "bakery", "cake", "puffs"),
                g("Café", "cafe", "starbucks", "ccd", "juice")
            ),
            keywords = listOf("food", "pizza", "burger", "saapadu")
        ),
        SeedCategory(
            "Groceries", "🛒", 0xFF2ECC71,
            listOf(
                m("Blinkit", "blinkit", "blink it"),
                m("Zepto", "zepto"),
                m("Instamart", "instamart", "insta mart"),
                m("BigBasket", "bigbasket", "big basket"),
                m("JioMart", "jiomart", "jio mart"),
                m("DMart", "dmart", "d mart"),
                m("Reliance Fresh", "reliance fresh", "reliance smart"),
                g("Kirana", "kirana", "maligai", "malligai", "மளிகை", "provision", "provisions", "rice", "arisi", "oil", "sugar"),
                g("Vegetables & Fruits", "vegetables", "vegetable", "veggies", "kaaikari", "kaikari", "காய்கறி",
                    "fruits", "fruit", "pazham", "பழம்"),
                g("Meat & Fish", "chicken", "mutton", "fish", "meen", "மீன்", "கோழி", "egg", "eggs", "muttai", "முட்டை")
            ),
            keywords = listOf("groceries", "grocery")
        ),
        SeedCategory(
            "Milk & Daily", "🥛", 0xFF74B9FF,
            listOf(
                m("Aavin", "aavin", "ஆவின்"),
                m("Arokya", "arokya", "arogya"),
                m("Heritage", "heritage"),
                g("Milkman", "milk", "paal", "pal", "பால்", "milkman"),
                m("Country Delight", "country delight"),
                g("Newspaper", "newspaper", "paper", "dinamalar", "dinathanthi", "செய்தித்தாள்"),
                g("Water Can", "water can", "can water", "drinking water", "thanni can")
            )
        ),
        SeedCategory(
            "Commute", "🛺", 0xFFFDCB6E,
            listOf(
                g("Petrol", "petrol", "பெட்ரோல்", "petrol bunk", "fuel"),
                g("Diesel", "diesel", "டீசல்"),
                m("Ola", "ola", "ஓலா"),
                m("Uber", "uber", "ஊபர்"),
                m("Rapido", "rapido"),
                g("Auto", "auto", "share auto", "ஆட்டோ"),
                g("Bus", "bus", "பஸ்", "பேருந்து"),
                g("Metro", "metro", "மெட்ரோ"),
                g("Parking", "parking"),
                g("FASTag / Toll", "fastag", "toll"),
                g("Vehicle Service", "bike service", "car service", "puncture", "mechanic", "service")
            ),
            keywords = listOf("travel", "commute", "cab", "taxi")
        ),
        SeedCategory(
            "Travel", "✈️", 0xFF00CEC9,
            listOf(
                m("IRCTC / Train", "irctc", "train", "ரயில்", "railway"),
                m("redBus", "redbus", "red bus"),
                g("Flights", "flight", "indigo", "air india"),
                g("Hotels", "oyo", "makemytrip", "hotel room", "lodge", "resort"),
                g("Trip Expenses", "trip", "tour")
            )
        ),
        SeedCategory(
            "Recharges", "📱", 0xFFA29BFE,
            listOf(
                m("Mobile – Self", "recharge", "mobile recharge", "prepaid", "postpaid", "jio", "airtel", "vi",
                    "vodafone", "bsnl", "ரீசார்ஜ்"),
                g("Mobile – Family"),
                m("DTH", "dth", "tata play", "tataplay", "sun direct", "airtel dth", "dish tv", "d2h"),
                m("Broadband", "broadband", "wifi", "jiofiber", "jio fiber", "airtel fiber", "airtel xstream",
                    "act fibernet", "internet")
            )
        ),
        SeedCategory(
            "OTT & Subscriptions", "🎬", 0xFFE84393,
            listOf(
                m("Netflix", "netflix", "நெட்ஃபிளிக்ஸ்"),
                m("Prime Video", "prime", "prime video", "amazon prime"),
                m("JioHotstar", "hotstar", "jiohotstar", "jio hotstar", "disney"),
                m("SonyLIV", "sonyliv", "sony liv"),
                m("ZEE5", "zee5", "zee 5", "zee"),
                m("Sun NXT", "sun nxt", "sunnxt", "sun next"),
                m("Aha", "aha"),
                m("YouTube Premium", "youtube", "youtube premium"),
                m("Spotify", "spotify"),
                m("Cloud Storage", "icloud", "google one", "cloud storage")
            ),
            keywords = listOf("subscription", "ott")
        ),
        SeedCategory(
            "Bills & Utilities", "💡", 0xFFF39C12,
            listOf(
                g("Electricity (EB)", "eb", "eb bill", "current bill", "electricity", "tneb", "மின்சாரம்", "கரண்ட்"),
                g("Water", "water bill", "water tax", "metro water"),
                m("LPG Cylinder", "cylinder", "gas cylinder", "lpg", "indane", "hp gas", "bharat gas", "சிலிண்டர்"),
                g("Piped Gas", "piped gas", "png"),
                g("Maintenance", "maintenance", "association")
            ),
            keywords = listOf("bill")
        ),
        SeedCategory(
            "Home & Help", "🏠", 0xFF6C5CE7,
            listOf(
                g("Rent", "rent", "house rent", "vaadagai", "vadagai", "வாடகை"),
                g("Housemaid", "maid", "housemaid", "velai aal", "velaikari", "servant", "வேலைக்காரி"),
                g("Cook", "cook", "samayal"),
                g("Driver", "driver"),
                g("Watchman", "watchman", "security"),
                g("Repairs", "repair", "plumber", "electrician", "carpenter", "painting"),
                m("Urban Company", "urban company", "urbanclap")
            )
        ),
        SeedCategory(
            "Shopping", "🛍️", 0xFFFF6B81,
            listOf(
                m("Amazon", "amazon", "அமேசான்"),
                m("Flipkart", "flipkart", "flip kart", "ஃபிளிப்கார்ட்"),
                m("Meesho", "meesho", "மீஷோ"),
                m("Myntra", "myntra"),
                m("Ajio", "ajio"),
                m("Nykaa", "nykaa", "nyka"),
                m("Croma / Reliance Digital", "croma", "reliance digital", "vijay sales", "poorvika", "sangeetha"),
                g("Local Store", "shop", "kadai", "dress", "saree", "clothes", "shirt", "chappal", "slippers", "துணி", "புடவை")
            ),
            keywords = listOf("shopping", "purchase")
        ),
        SeedCategory(
            "Health", "💊", 0xFFD63031,
            listOf(
                g("Pharmacy", "medicine", "medicines", "medical", "pharmacy", "tablets", "apollo", "medplus", "1mg",
                    "pharmeasy", "marundhu", "maathirai", "மருந்து", "மாத்திரை"),
                g("Doctor", "doctor", "clinic", "hospital", "consultation", "டாக்டர்", "மருத்துவமனை"),
                g("Lab Tests", "lab", "blood test", "lab test", "scan", "x ray", "xray"),
                g("Health Insurance", "health insurance", "star health", "mediclaim")
            )
        ),
        SeedCategory(
            "Education", "🎓", 0xFF0984E3,
            listOf(
                g("School Fees", "school", "school fees", "பள்ளி"),
                g("College Fees", "college", "college fees"),
                g("Tuition", "tuition", "tution", "coaching"),
                g("Books", "books", "book", "notebook", "stationery", "புத்தகம்"),
                g("Courses", "course", "udemy", "coursera")
            ),
            keywords = listOf("fees")
        ),
        SeedCategory(
            "Personal Care", "💇", 0xFFFD79A8,
            listOf(
                g("Salon", "salon", "saloon", "haircut", "hair cut", "parlour", "parlor", "barber"),
                g("Gym", "gym"),
                g("Cosmetics", "cosmetics", "makeup", "cream", "soap", "shampoo")
            )
        ),
        SeedCategory(
            "Gifts & Occasions", "🎁", 0xFFE17055,
            listOf(
                g("Wedding Moi", "moi", "மொய்", "wedding", "marriage", "kalyanam", "கல்யாணம்"),
                g("Birthday", "birthday", "bday"),
                g("Festival", "pongal", "deepavali", "diwali", "christmas", "ramzan", "festival", "crackers", "pattasu"),
                g("Temple / Donation", "temple", "kovil", "koil", "கோவில்", "donation", "undiyal", "hundi",
                    "archanai", "church", "mosque")
            ),
            keywords = listOf("gift", "present")
        ),
        SeedCategory(
            "Entertainment & Party", "🎉", 0xFF00B5A5,
            listOf(
                g("Movies", "movie", "cinema", "film", "bookmyshow", "pvr", "inox", "theatre", "படம்"),
                g("Outing", "outing", "beach", "park"),
                g("Party", "party", "treat", "drinks", "bar")
            )
        ),
        SeedCategory(
            "EMIs & Loans", "🏦", 0xFF636E72,
            listOf(
                g("Home Loan EMI", "home loan", "housing loan"),
                g("Vehicle EMI", "bike emi", "car emi", "vehicle loan"),
                g("Personal Loan EMI", "personal loan", "emi"),
                SeedSub("Credit Card Bill", listOf("credit card bill", "card bill", "cc bill"), merchant = true, transfer = true),
                g("Money Lent", "lent", "kadan", "கடன்")
            ),
            keywords = listOf("loan")
        ),
        SeedCategory(
            "Kids & Family", "🧸", 0xFFFFA502,
            listOf(
                g("Toys", "toys", "toy"),
                g("Kids' Clothes", "kids dress"),
                g("Pocket Money", "pocket money"),
                g("Elders' Care", "elders")
            ),
            keywords = listOf("kids", "chocolate")
        ),
        SeedCategory(
            "Savings & Investments", "💰", 0xFF00B894,
            listOf(
                g("SIP / Mutual Fund", "sip", "mutual fund", "mf"),
                g("RD / FD", "rd", "fd", "fixed deposit", "recurring deposit"),
                g("Gold Scheme", "gold scheme", "gold", "nagai", "jewellery scheme", "தங்கம்"),
                g("Chit Fund", "chit", "cheetu", "chit fund", "சீட்டு"),
                g("PPF / NPS", "ppf", "nps"),
                g("Stocks", "stocks", "shares", "zerodha")
            ),
            keywords = listOf("savings", "invest", "investment"),
            kind = CategoryKind.SAVING
        )
    )
}
