<p align="center">
  <img src="app/src/main/res/drawable-nodpi/cordicart_logo.png" alt="CordiCart, campus marketplace" width="180">
</p>

<h1 align="center">CordiCart</h1>

<p align="center">
  A campus-exclusive marketplace for University of the Cordilleras students:
  buy, sell, or trade pre-owned textbooks, uniforms, and school gear with fellow students.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-1F4D3A">
  <img alt="Language" src="https://img.shields.io/badge/language-Kotlin-1F4D3A">
  <img alt="Backend" src="https://img.shields.io/badge/backend-Firebase-F4C76A">
  <img alt="SDG 12" src="https://img.shields.io/badge/SDG-12%20Responsible%20Consumption-F4C76A">
</p>

---

## About

Every semester, students buy textbooks, uniforms, and drafting kits they only need for one term. Afterwards,
many of these items sit unused or get thrown away while incoming students pay full price for the same things.

**CordiCart** gives UC students a closed, trusted marketplace to pass these items on to the next batch. Only
verified `@students.uc-bcf.edu.ph` accounts can join, and listings are organized by course code and category,
so students can find exactly what a class requires.

### SDG alignment

**SDG 12: Responsible Consumption and Production.** CordiCart extends the life of semester-specific
academic goods by recirculating them across student batches instead of letting them go to waste, while
making school supplies more affordable.

---

## Features

### 1. Institutional student verification
- Registration accepts **only** `@students.uc-bcf.edu.ph` email addresses (full format check, case-insensitive).
- A **6-digit one-time code** confirms the student owns the school email.
  - The code expires after **5 minutes** and allows **5 attempts**.
  - There is a **60-second** resend cooldown.
  - Only a SHA-256 hash of the code is stored, and a used code is deleted so it can't be reused.
- Students pick their **college** (COA, CAS, CBA, CCJE, CAFA, CHTM, CITCS, COL, CTE, CONAHS).
- Verified students stay logged in. Unverified students are always sent back to finish verification.

### 2. Course-coded marketplace feed
- Live 2-column feed of listings that updates on every device in real time (no refresh needed).
- **Search** by title, course code, category, or college (ignores spaces and capital letters, so `math101` finds `MATH 101`).
- **Category filters** that work together with search:
  Textbooks · Uniforms & PE · Drafting & Art Supplies · Lab & Clinical Gear · Calculators & Gadgets · Others.
- **Post an item** with category, condition (Like New / Gently Used / Used), course code, price in ₱,
  an *Open to trade* option for bartering, and a description.
- Course codes are standardized automatically (`math101` → `MATH 101`).
- Listing details with seller name and college. Students can't message themselves about their own listing.

### 3. In-app chat
- **Message seller** on a listing opens a private chat between the buyer and the seller about that item.
- Messages appear instantly on both phones (real-time Firestore listeners), newest at the bottom.
- Quick questions for the buyer's first message: *Is this still available?*, *Can we meet on campus?*, *Is the price negotiable?*
- **Messages** tab lists every chat you're in, as a buyer or a seller, with the latest message and time.
- Each chat shows the listing it's about; tap it to open the item.
- The chat is only created when the first message is sent, so no empty chats are left behind.
- Security rules make sure only the buyer and the seller can read a chat, and that nobody can send as someone else.
- **Unread indicators:** a red badge on the Messages tab counts unread chats, and unread chats show a bold preview with an amber dot.
- **New-message notifications** while the app is open or running in the background. Tapping one opens that chat. (Notifications when the app is fully closed would need Firebase Cloud Messaging and the paid Blaze plan.)

---

## Screens

| # | Screen | Activity | Layout |
|---|---|---|---|
| 1 | Log in (launcher) | `MainActivity` | `activity_main.xml` |
| 2 | Create account | `RegisterActivity` | `activity_register.xml` |
| 3 | Verify your email | `VerifyEmailActivity` | `activity_verify_email.xml` |
| 4 | Marketplace dashboard | `FeedActivity` | `activity_feed.xml`, `item_market_card.xml`, `sheet_profile.xml` |
| 5 | Post an item | `PostItemActivity` | `activity_post_item.xml` |
| 6 | Listing details | `ListingDetailActivity` | `activity_listing_detail.xml` |
| 7 | Messages | `ChatListActivity` | `activity_chat_list.xml`, `item_chat.xml` |
| 8 | Chat | `ChatActivity` | `activity_chat.xml`, `item_message.xml` |

<!-- Add screenshots here, e.g.:
<p align="center">
  <img src="docs/screenshots/login.png" width="220">
  <img src="docs/screenshots/dashboard.png" width="220">
  <img src="docs/screenshots/post.png" width="220">
</p>
-->

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | XML layouts (Android Views), Material Components 3 |
| Authentication | Firebase Authentication (email and password) |
| Database | Cloud Firestore (real-time listeners) |
| Email (optional) | JavaMail over Gmail SMTP, for sending the verification code |
| Min / target SDK | 24 / 37 |

### Design

| Role | Color |
|---|---|
| Pine green (primary) | `#1F4D3A` |
| Amber (accent) | `#F4C76A` |
| Ink (text) | `#1B2420` |
| Background | `#F6F4EE` |

Fonts: **Manrope** (body) and **Bricolage Grotesque** (headings), both from Google Fonts.

---

## Project structure

```
app/src/main/
├── java/com/cordicart/cordicart/
│   ├── MainActivity.kt            # 1 · Log in, routes signed-in students
│   ├── RegisterActivity.kt        # 2 · Create account
│   ├── VerifyEmailActivity.kt     # 3 · 6-digit code verification
│   ├── FeedActivity.kt            # 4 · Marketplace dashboard
│   ├── PostItemActivity.kt        # 5 · Post an item
│   ├── ListingDetailActivity.kt   # 6 · Listing details
│   ├── ChatListActivity.kt        # 7 · Messages (all your chats)
│   ├── ChatActivity.kt            # 8 · One chat with a buyer or seller
│   ├── Chat.kt                    # Chat and message models
│   ├── ChatRepository.kt          # Firestore access for chats
│   ├── ChatNotifier.kt            # Unread badge and new-message notifications
│   ├── AuthRouter.kt              # Sends each student to the right screen
│   ├── OtpManager.kt              # Generates, stores (hashed), and checks codes
│   ├── MailSender.kt / MailConfig.kt  # Optional Gmail SMTP sending
│   ├── EmailValidator.kt          # UC student email check
│   ├── MarketItem.kt              # Listing model, search, course-code rules
│   ├── MarketRepository.kt        # Firestore access for listings
│   ├── MarketItemAdapter.kt       # Listing cards in the grid
│   ├── Categories.kt              # The 6 categories, icons, and colors
│   ├── ExpandedGridView.kt        # Lets the dashboard scroll as one page
│   ├── Ui.kt                      # Shared UI helpers
│   └── CordiCartApp.kt            # App startup settings
├── res/
│   ├── layout/                    # Screen layouts
│   ├── drawable/                  # Icons and shapes
│   ├── drawable-nodpi/            # Logo and launcher icon art
│   ├── font/                      # Manrope, Bricolage Grotesque
│   └── values/                    # colors, strings, styles, themes
└── AndroidManifest.xml
firestore.rules                     # Firestore security rules
```

---

## Getting started

### Requirements
- Android Studio (latest stable)
- A Firebase project (the free Spark plan is enough)
- An emulator or Android phone. Testing the live feed works best with **two** devices and **two** UC accounts.

### Setup
1. **Clone the repository** and open it in Android Studio.
2. **Connect Firebase**
   - In the [Firebase console](https://console.firebase.google.com), add an Android app with package name
     `com.cordicart.cordicart`.
   - Download `google-services.json` into the `app/` folder.
   - Enable **Authentication → Sign-in method → Email/Password**.
   - Create a **Firestore database**, then paste the contents of `firestore.rules` into the **Rules** tab and click **Publish**.
3. **Sync and run:** File → Sync Project with Gradle Files, then Run.

### Email sending (optional)
By default the app runs in **demo mode**: the verification code is shown in a dialog instead of being emailed.
To send real emails:
1. Create a dedicated Gmail account and turn on 2-Step Verification.
2. Create an **App Password**.
3. Put both values in `MailConfig.kt`.

> ⚠️ Never commit real email credentials to a public repository.

---

## Firestore data model

```
users/{uid}          uid, email, displayName, department, verified, createdAt
otps/{uid}           codeHash, expiresAt, attempts, sentAt        (deleted after use)
listings/{id}        title, category, courseCode, condition, price, openToTrade,
                     description, sellerId, sellerName, sellerDept, status, createdAt
chats/{listingId}_{buyerId}
                     listingId, listingTitle, listingCategory, listingPrice,
                     buyerId, buyerName, sellerId, sellerName, participants,
                     lastMessage, lastSenderId, updatedAt, lastRead {uid: time}, createdAt
chats/{chatId}/messages/{id}
                     senderId, text, createdAt
```

Security rules allow only signed-in UC student accounts. Only **verified** students can read or post listings,
and students can edit only their own profile and listings.

---

## Known limitations

These are known limitations of the current version:

- **Demo mode:** without SMTP credentials, the code is shown on screen instead of emailed.
- **Credentials in the app:** when email sending is enabled, the Gmail app password is stored inside the app.
  A production version would send emails from a server, such as Cloud Functions.
- **Client-side verification:** the database rules let students update their own profile, so a modified app
  could set its own `verified` flag. A server-side check would close this.
- **Device clock:** code expiry is checked against the phone's clock.
- **Resend timer:** the 60-second resend cooldown restarts if the screen is rotated or the app is reopened.
- **Department search:** searching a college name also matches listings posted by students from that college.
- **Seller name timing:** a listing posted the instant the screen opens may show the default seller name.
- **Not built yet:** photo upload, saved items, notifications, advanced filters, and password reset
  show a "coming soon" message.
- **Scale:** the feed loads all listings and filters them on the phone, which is fine at campus scale.

---

## Roadmap

- [x] In-app chat between buyers and sellers
- [ ] Seller photo uploads
- [ ] Safe campus meetup spots and **Confirm Handoff & Mark Sold**
- [ ] Class-break pickup scheduler
- [ ] Textbook lookup by ISBN or title (e.g. Open Library API)
- [ ] Saved items and notifications

---

## Team

<!-- Replace with your team members -->
| Name | Role |
|---|---|
| Member 1 | |
| Member 2 | |
| Member 3 | |

Developed as a laboratory project at the **University of the Cordilleras**.
