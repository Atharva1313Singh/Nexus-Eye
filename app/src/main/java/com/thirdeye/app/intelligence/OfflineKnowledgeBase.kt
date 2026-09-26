package com.thirdeye.app.intelligence

object OfflineKnowledgeBase {

    val questions = listOf(

        // ------------------------------------------------------------
        // FRIENDLY / CONVERSATIONAL QUESTIONS
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "hi",
                "hii",
                "hiii",
                "hello",
                "hey",
                "hello there"
            ),
            englishAnswer =
                "Hello! I am NEXUS EYE. How can I help you?",
            hindiAnswer =
                "नमस्ते! मैं NEXUS EYE हूँ। मैं आपकी कैसे मदद कर सकता हूँ?"
        ),

        OfflineQuestion(
            keywords = listOf(
                "good morning",
                "morning"
            ),
            englishAnswer =
                "Good morning! I am ready to help you.",
            hindiAnswer =
                "सुप्रभात! मैं आपकी मदद करने के लिए तैयार हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "good afternoon",
                "afternoon"
            ),
            englishAnswer =
                "Good afternoon! How can I help you?",
            hindiAnswer =
                "नमस्कार! मैं आपकी कैसे मदद कर सकता हूँ?"
        ),

        OfflineQuestion(
            keywords = listOf(
                "good evening",
                "evening"
            ),
            englishAnswer =
                "Good evening! How can I help you?",
            hindiAnswer =
                "शुभ संध्या! मैं आपकी कैसे मदद कर सकता हूँ?"
        ),

        OfflineQuestion(
            keywords = listOf(
                "good night",
                "night"
            ),
            englishAnswer =
                "Good night! Take care and stay safe.",
            hindiAnswer =
                "शुभ रात्रि! अपना ध्यान रखें और सुरक्षित रहें।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how are you",
                "how are you doing",
                "how do you feel",
                "are you okay"
            ),
            englishAnswer =
                "I am doing well and ready to help you.",
            hindiAnswer =
                "मैं ठीक हूँ और आपकी मदद करने के लिए तैयार हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how is your day",
                "how is your day going"
            ),
            englishAnswer =
                "My day is going well. I am here and ready to help.",
            hindiAnswer =
                "मेरा दिन अच्छा जा रहा है। मैं यहाँ हूँ और आपकी मदद करने के लिए तैयार हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what are you doing",
                "what are you doing now"
            ),
            englishAnswer =
                "I am here waiting for your next request.",
            hindiAnswer =
                "मैं यहाँ आपकी अगली बात या आदेश का इंतज़ार कर रहा हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "are you there",
                "are you listening",
                "can you hear me"
            ),
            englishAnswer =
                "Yes, I am here and listening.",
            hindiAnswer =
                "हाँ, मैं यहाँ हूँ और आपकी बात सुन रहा हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "can you help me",
                "help me",
                "i need help"
            ),
            englishAnswer =
                "Yes. Tell me what you need help with.",
            hindiAnswer =
                "हाँ। बताइए आपको किस चीज़ में मदद चाहिए।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "nice to meet you",
                "glad to meet you"
            ),
            englishAnswer =
                "Nice to meet you too. I am ready to help.",
            hindiAnswer =
                "आपसे मिलकर मुझे भी खुशी हुई। मैं आपकी मदद के लिए तैयार हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "thank you",
                "thanks",
                "thankyou"
            ),
            englishAnswer =
                "You're welcome!",
            hindiAnswer =
                "आपका स्वागत है!"
        ),

        OfflineQuestion(
            keywords = listOf(
                "you are helpful",
                "you are very helpful",
                "you helped me"
            ),
            englishAnswer =
                "Thank you! I am happy to help.",
            hindiAnswer =
                "धन्यवाद! मुझे आपकी मदद करके खुशी होती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "good job",
                "well done",
                "great job"
            ),
            englishAnswer =
                "Thank you! I will keep doing my best.",
            hindiAnswer =
                "धन्यवाद! मैं अपना सर्वश्रेष्ठ करने की कोशिश करता रहूँगा।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "who are you",
                "what are you"
            ),
            englishAnswer =
                "I am NEXUS EYE, your assistive AI system.",
            hindiAnswer =
                "मैं NEXUS EYE हूँ, आपका सहायक AI सिस्टम।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what can you do",
                "what do you do",
                "what can nexus eye do"
            ),
            englishAnswer =
                "I can help with questions, device communication, and assistive functions. More capabilities will be added as NEXUS EYE development continues.",
            hindiAnswer =
                "मैं प्रश्नों, डिवाइस कम्युनिकेशन और सहायक कार्यों में मदद कर सकता हूँ। NEXUS EYE के विकास के साथ और सुविधाएँ जोड़ी जाएँगी।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "bye",
                "goodbye",
                "see you",
                "see you later"
            ),
            englishAnswer =
                "Goodbye! Take care.",
            hindiAnswer =
                "अलविदा! अपना ध्यान रखें।"
        ),

        // ------------------------------------------------------------
        // NEXUS EYE PROJECT
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "what is nexus eye",
                "tell me about nexus eye",
                "what is nexus eye system"
            ),
            englishAnswer =
                "NEXUS EYE is an assistive system combining an Android phone and an ESP32 wearable.",
            hindiAnswer =
                "NEXUS EYE एक सहायक सिस्टम है जो Android फोन और ESP32 wearable को जोड़ता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "who made you",
                "who created you",
                "who developed you"
            ),
            englishAnswer =
                "I am part of the NEXUS EYE assistive system made by Atharv Singh, Akarsh Singh And Anshuman Tiwari.",
            hindiAnswer =
                "मैं Atharv Singh द्वारा बनाए गए NEXUS EYE सहायक सिस्टम का हिस्सा हूँ।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "where does nexus eye speak",
                "where does nexus eye give speech",
                "where is the final speech output"
            ),
            englishAnswer =
                "The final assistant speech is designed to come from the NEXUS EYE wearable speaker.",
            hindiAnswer =
                "अंतिम assistant speech NEXUS EYE wearable speaker से आने के लिए डिज़ाइन की गई है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "who processes advanced tasks",
                "who handles advanced tasks",
                "android advanced processing"
            ),
            englishAnswer =
                "Android handles advanced processing while the ESP32 handles simple and fast local tasks first.",
            hindiAnswer =
                "Android advanced processing संभालता है जबकि ESP32 पहले simple और fast local tasks संभालता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is offline first",
                "what does offline first mean",
                "offline first"
            ),
            englishAnswer =
                "Offline-first design prioritizes local processing and local data whenever the required function can work without the Internet.",
            hindiAnswer =
                "Offline-first design में जहाँ संभव हो वहाँ Internet के बिना local processing और local data को प्राथमिकता दी जाती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what happens when a question is not offline",
                "question not in offline database",
                "question not found offline"
            ),
            englishAnswer =
                "When a question is not available locally, NEXUS EYE can use its online fallback when Internet access is available.",
            hindiAnswer =
                "जब कोई प्रश्न local data में उपलब्ध नहीं होता, तब Internet उपलब्ध होने पर NEXUS EYE online fallback का उपयोग कर सकता है।"
        ),

        // ------------------------------------------------------------
        // INDIA / GENERAL KNOWLEDGE
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "capital of india",
                "india capital"
            ),
            englishAnswer =
                "The capital of India is New Delhi.",
            hindiAnswer =
                "भारत की राजधानी नई दिल्ली है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "capital of france",
                "france capital"
            ),
            englishAnswer =
                "The capital of France is Paris.",
            hindiAnswer =
                "फ्रांस की राजधानी पेरिस है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "capital of japan",
                "japan capital"
            ),
            englishAnswer =
                "The capital of Japan is Tokyo.",
            hindiAnswer =
                "जापान की राजधानी टोक्यो है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "capital of usa",
                "capital of united states",
                "america capital"
            ),
            englishAnswer =
                "The capital of the United States is Washington, D.C.",
            hindiAnswer =
                "संयुक्त राज्य अमेरिका की राजधानी वॉशिंगटन डी.सी. है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how many states in india",
                "number of states in india",
                "states in india"
            ),
            englishAnswer =
                "India has 28 states and 8 Union Territories.",
            hindiAnswer =
                "भारत में 28 राज्य और 8 केंद्र शासित प्रदेश हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "currency of india",
                "indian currency"
            ),
            englishAnswer =
                "The currency of India is the Indian rupee.",
            hindiAnswer =
                "भारत की मुद्रा भारतीय रुपया है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national animal of india",
                "india national animal"
            ),
            englishAnswer =
                "The national animal of India is the Bengal tiger.",
            hindiAnswer =
                "भारत का राष्ट्रीय पशु बंगाल टाइगर है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national bird of india",
                "india national bird"
            ),
            englishAnswer =
                "The national bird of India is the Indian peafowl.",
            hindiAnswer =
                "भारत का राष्ट्रीय पक्षी भारतीय मोर है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national flower of india",
                "india national flower"
            ),
            englishAnswer =
                "The national flower of India is the lotus.",
            hindiAnswer =
                "भारत का राष्ट्रीय फूल कमल है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national tree of india",
                "india national tree"
            ),
            englishAnswer =
                "The national tree of India is the banyan tree.",
            hindiAnswer =
                "भारत का राष्ट्रीय वृक्ष बरगद है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national fruit of india",
                "india national fruit"
            ),
            englishAnswer =
                "The mango is the national fruit of India.",
            hindiAnswer =
                "आम भारत का राष्ट्रीय फल है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national anthem of india",
                "india national anthem"
            ),
            englishAnswer =
                "The national anthem of India is Jana Gana Mana.",
            hindiAnswer =
                "भारत का राष्ट्रगान जन गण मन है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "national song of india",
                "india national song"
            ),
            englishAnswer =
                "Vande Mataram is the national song of India.",
            hindiAnswer =
                "वंदे मातरम् भारत का राष्ट्रीय गीत है।"
        ),

        // ------------------------------------------------------------
        // SPACE / SCIENCE
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "largest planet",
                "biggest planet"
            ),
            englishAnswer =
                "Jupiter is the largest planet in our Solar System.",
            hindiAnswer =
                "बृहस्पति हमारे सौरमंडल का सबसे बड़ा ग्रह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "smallest planet"
            ),
            englishAnswer =
                "Mercury is the smallest planet in our Solar System.",
            hindiAnswer =
                "बुध हमारे सौरमंडल का सबसे छोटा ग्रह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "closest planet to sun",
                "nearest planet to sun"
            ),
            englishAnswer =
                "Mercury is the closest planet to the Sun.",
            hindiAnswer =
                "बुध सूर्य के सबसे निकट का ग्रह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "red planet"
            ),
            englishAnswer =
                "Mars is commonly called the Red Planet.",
            hindiAnswer =
                "मंगल ग्रह को आमतौर पर लाल ग्रह कहा जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "planet with rings",
                "planet famous for rings"
            ),
            englishAnswer =
                "Saturn is famous for its extensive ring system.",
            hindiAnswer =
                "शनि अपने विशाल वलय तंत्र के लिए प्रसिद्ध है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how many planets",
                "number of planets"
            ),
            englishAnswer =
                "There are eight recognized planets in our Solar System.",
            hindiAnswer =
                "हमारे सौरमंडल में आठ मान्यता प्राप्त ग्रह हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is the sun",
                "what is sun"
            ),
            englishAnswer =
                "The Sun is the star at the center of our Solar System.",
            hindiAnswer =
                "सूर्य हमारे सौरमंडल के केंद्र में स्थित एक तारा है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is moon",
                "what is the moon"
            ),
            englishAnswer =
                "The Moon is Earth's natural satellite.",
            hindiAnswer =
                "चंद्रमा पृथ्वी का प्राकृतिक उपग्रह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is galaxy",
                "what are galaxies"
            ),
            englishAnswer =
                "A galaxy is a huge system of stars, gas, dust, and other matter held together by gravity.",
            hindiAnswer =
                "आकाशगंगा तारों, गैस, धूल और अन्य पदार्थों की विशाल गुरुत्वाकर्षण से बंधी प्रणाली है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is milky way",
                "what is the milky way"
            ),
            englishAnswer =
                "The Milky Way is the galaxy that contains our Solar System.",
            hindiAnswer =
                "मिल्की वे वह आकाशगंगा है जिसमें हमारा सौरमंडल स्थित है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is black hole",
                "black hole",
                "black holes",
                "what are black holes"
            ),
            englishAnswer =
                "A black hole is an extremely dense region of spacetime from which light cannot escape after crossing the event horizon.",
            hindiAnswer =
                "ब्लैक होल अंतरिक्ष-समय का अत्यंत घना क्षेत्र है जहाँ event horizon पार करने के बाद प्रकाश बाहर नहीं निकल सकता।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is asteroid",
                "asteroid"
            ),
            englishAnswer =
                "An asteroid is a small rocky body that orbits the Sun.",
            hindiAnswer =
                "क्षुद्रग्रह एक छोटा चट्टानी पिंड है जो सूर्य की परिक्रमा करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is comet",
                "comet"
            ),
            englishAnswer =
                "A comet is an icy body that can develop a glowing coma and tail when it approaches the Sun.",
            hindiAnswer =
                "धूमकेतु एक बर्फीला पिंड है जो सूर्य के पास आने पर चमकीला कोमा और पूँछ विकसित कर सकता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "speed of light",
                "how fast is light"
            ),
            englishAnswer =
                "The speed of light in vacuum is approximately 299,792 kilometers per second.",
            hindiAnswer =
                "निर्वात में प्रकाश की गति लगभग 299,792 किलोमीटर प्रति सेकंड है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "speed of sound",
                "how fast is sound"
            ),
            englishAnswer =
                "At about 20 degrees Celsius, sound travels through air at roughly 343 meters per second.",
            hindiAnswer =
                "लगभग 20 डिग्री सेल्सियस पर हवा में ध्वनि लगभग 343 मीटर प्रति सेकंड की गति से चलती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "boiling point of water",
                "water boiling point"
            ),
            englishAnswer =
                "Water boils at about 100 degrees Celsius at standard atmospheric pressure.",
            hindiAnswer =
                "सामान्य वायुमंडलीय दबाव पर पानी लगभग 100 डिग्री सेल्सियस पर उबलता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "freezing point of water",
                "water freezing point"
            ),
            englishAnswer =
                "Water freezes at about 0 degrees Celsius at standard atmospheric pressure.",
            hindiAnswer =
                "सामान्य वायुमंडलीय दबाव पर पानी लगभग 0 डिग्री सेल्सियस पर जमता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "chemical formula of water",
                "formula of water"
            ),
            englishAnswer =
                "The chemical formula of water is H2O.",
            hindiAnswer =
                "पानी का रासायनिक सूत्र H2O है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is gravity"
            ),
            englishAnswer =
                "Gravity is the force that attracts objects with mass toward one another.",
            hindiAnswer =
                "गुरुत्वाकर्षण वह बल है जो द्रव्यमान वाली वस्तुओं को एक-दूसरे की ओर आकर्षित करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is energy"
            ),
            englishAnswer =
                "Energy is the capacity to do work or cause change.",
            hindiAnswer =
                "ऊर्जा कार्य करने या परिवर्तन उत्पन्न करने की क्षमता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is electricity"
            ),
            englishAnswer =
                "Electricity involves electric charge and its movement.",
            hindiAnswer =
                "विद्युत विद्युत आवेश और उसकी गति से संबंधित है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is voltage"
            ),
            englishAnswer =
                "Voltage is the electrical potential difference between two points.",
            hindiAnswer =
                "वोल्टेज दो बिंदुओं के बीच विद्युत विभवांतर है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is current",
                "what is electric current"
            ),
            englishAnswer =
                "Electric current is the flow of electric charge.",
            hindiAnswer =
                "विद्युत धारा विद्युत आवेश का प्रवाह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is resistance",
                "what is electrical resistance"
            ),
            englishAnswer =
                "Electrical resistance describes how strongly a material opposes electric current.",
            hindiAnswer =
                "विद्युत प्रतिरोध बताता है कि कोई पदार्थ विद्युत धारा के प्रवाह का कितना विरोध करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is photosynthesis"
            ),
            englishAnswer =
                "Photosynthesis is the process by which plants use light energy to make food from carbon dioxide and water.",
            hindiAnswer =
                "प्रकाश संश्लेषण वह प्रक्रिया है जिसमें पौधे प्रकाश ऊर्जा का उपयोग करके कार्बन डाइऑक्साइड और पानी से भोजन बनाते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is oxygen"
            ),
            englishAnswer =
                "Oxygen is a chemical element essential for many forms of respiration and combustion.",
            hindiAnswer =
                "ऑक्सीजन एक रासायनिक तत्व है जो कई प्रकार की श्वसन प्रक्रियाओं और दहन के लिए आवश्यक है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is carbon dioxide"
            ),
            englishAnswer =
                "Carbon dioxide is a gas consisting of one carbon atom and two oxygen atoms.",
            hindiAnswer =
                "कार्बन डाइऑक्साइड एक गैस है जिसमें एक कार्बन परमाणु और दो ऑक्सीजन परमाणु होते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is atom"
            ),
            englishAnswer =
                "An atom is the basic unit of an element that retains the chemical properties of that element.",
            hindiAnswer =
                "परमाणु किसी तत्व की मूल इकाई है जिसमें उस तत्व के रासायनिक गुण बने रहते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is molecule"
            ),
            englishAnswer =
                "A molecule is a group of atoms held together by chemical bonds.",
            hindiAnswer =
                "अणु रासायनिक बंधों से जुड़े परमाणुओं का समूह है।"
        ),

        // ------------------------------------------------------------
        // TECHNOLOGY
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "what is ai",
                "what is artificial intelligence",
                "artificial intelligence"
            ),
            englishAnswer =
                "Artificial intelligence is technology that enables computers to perform tasks that normally require human intelligence.",
            hindiAnswer =
                "कृत्रिम बुद्धिमत्ता ऐसी तकनीक है जो कंप्यूटरों को सामान्यतः मानव बुद्धि की आवश्यकता वाले कार्य करने में सक्षम बनाती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is machine learning",
                "machine learning"
            ),
            englishAnswer =
                "Machine learning is a method where computers learn patterns from data.",
            hindiAnswer =
                "मशीन लर्निंग एक ऐसी विधि है जिसमें कंप्यूटर डेटा से पैटर्न सीखते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is bluetooth",
                "bluetooth"
            ),
            englishAnswer =
                "Bluetooth is a short-range wireless communication technology.",
            hindiAnswer =
                "Bluetooth कम दूरी की वायरलेस संचार तकनीक है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is ble",
                "bluetooth low energy",
                "ble"
            ),
            englishAnswer =
                "Bluetooth Low Energy is a Bluetooth technology designed for low-power wireless communication.",
            hindiAnswer =
                "Bluetooth Low Energy कम ऊर्जा वाली वायरलेस संचार तकनीक है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is esp32",
                "esp32"
            ),
            englishAnswer =
                "ESP32 is a family of low-cost microcontrollers with wireless connectivity and many hardware interfaces.",
            hindiAnswer =
                "ESP32 कम लागत वाले microcontrollers का परिवार है जिसमें wireless connectivity और कई hardware interfaces होते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is sensor"
            ),
            englishAnswer =
                "A sensor detects physical conditions such as light, temperature, motion, or pressure.",
            hindiAnswer =
                "सेंसर प्रकाश, तापमान, गति या दबाव जैसी भौतिक स्थितियों का पता लगाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is camera"
            ),
            englishAnswer =
                "A camera captures images or video using an optical system and an image sensor.",
            hindiAnswer =
                "कैमरा optical system और image sensor की सहायता से चित्र या वीडियो कैप्चर करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is ocr"
            ),
            englishAnswer =
                "OCR stands for Optical Character Recognition. It converts text in images into machine-readable text.",
            hindiAnswer =
                "OCR का अर्थ Optical Character Recognition है। यह चित्रों में मौजूद टेक्स्ट को मशीन द्वारा पढ़े जा सकने वाले टेक्स्ट में बदलता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is gps"
            ),
            englishAnswer =
                "GPS is a satellite-based positioning system used to determine location and provide navigation information.",
            hindiAnswer =
                "GPS एक उपग्रह आधारित स्थिति निर्धारण प्रणाली है जिसका उपयोग स्थान और नेविगेशन जानकारी प्राप्त करने के लिए किया जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is computer",
                "what is a computer"
            ),
            englishAnswer =
                "A computer is an electronic device that processes data according to instructions.",
            hindiAnswer =
                "कंप्यूटर एक इलेक्ट्रॉनिक उपकरण है जो निर्देशों के अनुसार डेटा को संसाधित करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is android"
            ),
            englishAnswer =
                "Android is a mobile operating system and software platform developed by Google and the open-source community.",
            hindiAnswer =
                "Android एक मोबाइल operating system और software platform है जिसे Google और open-source समुदाय ने विकसित किया है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is kotlin"
            ),
            englishAnswer =
                "Kotlin is a modern programming language widely used for Android development.",
            hindiAnswer =
                "Kotlin एक आधुनिक programming language है जिसका Android development में व्यापक उपयोग होता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is internet"
            ),
            englishAnswer =
                "The Internet is a global network of interconnected computer networks.",
            hindiAnswer =
                "इंटरनेट आपस में जुड़े कंप्यूटर नेटवर्कों का वैश्विक नेटवर्क है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is website"
            ),
            englishAnswer =
                "A website is a collection of related web pages and resources available through the web.",
            hindiAnswer =
                "वेबसाइट संबंधित web pages और resources का संग्रह है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is database"
            ),
            englishAnswer =
                "A database is an organized collection of data that can be stored, managed, and retrieved.",
            hindiAnswer =
                "डेटाबेस डेटा का एक व्यवस्थित संग्रह है जिसे संग्रहीत, प्रबंधित और प्राप्त किया जा सकता है।"
        ),


        OfflineQuestion(
            keywords = listOf(
                "what is rom"
            ),
            englishAnswer =
                "ROM is non-volatile memory designed to retain stored information without continuous power.",
            hindiAnswer =
                "ROM ऐसी non-volatile memory है जो लगातार बिजली के बिना भी अपनी जानकारी बनाए रखती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is cpu"
            ),
            englishAnswer =
                "The CPU is the main processor that executes instructions in a computer.",
            hindiAnswer =
                "CPU मुख्य processor है जो कंप्यूटर में instructions को चलाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is gpu"
            ),
            englishAnswer =
                "A GPU is a processor specialized for highly parallel graphics and computation workloads.",
            hindiAnswer =
                "GPU ऐसा processor है जो समानांतर graphics और computation कार्यों के लिए विशेष रूप से बनाया जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is wifi",
                "what is wi fi"
            ),
            englishAnswer =
                "Wi-Fi is a wireless networking technology used to connect devices to local networks.",
            hindiAnswer =
                "Wi-Fi एक wireless networking technology है जिसका उपयोग उपकरणों को local networks से जोड़ने के लिए किया जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is password"
            ),
            englishAnswer =
                "A password is a secret string used to help authenticate a user or protect access.",
            hindiAnswer =
                "पासवर्ड एक गुप्त string है जिसका उपयोग उपयोगकर्ता की पहचान सत्यापित करने या access सुरक्षित करने के लिए किया जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is encryption"
            ),
            englishAnswer =
                "Encryption transforms data so that it cannot be easily read without the appropriate key or method.",
            hindiAnswer =
                "एन्क्रिप्शन डेटा को इस प्रकार बदलता है कि उचित key या विधि के बिना उसे आसानी से पढ़ा नहीं जा सकता।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is cybersecurity"
            ),
            englishAnswer =
                "Cybersecurity protects computers, networks, applications, and data from unauthorized access and attacks.",
            hindiAnswer =
                "साइबर सुरक्षा कंप्यूटर, नेटवर्क, एप्लिकेशन और डेटा को अनधिकृत पहुँच और हमलों से बचाती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is phishing"
            ),
            englishAnswer =
                "Phishing is a fraudulent attempt to obtain sensitive information by pretending to be a trusted person or organization.",
            hindiAnswer =
                "फिशिंग एक धोखाधड़ी वाला प्रयास है जिसमें भरोसेमंद व्यक्ति या संस्था का रूप लेकर संवेदनशील जानकारी प्राप्त करने की कोशिश की जाती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is malware"
            ),
            englishAnswer =
                "Malware is software designed to damage, disrupt, spy on, or gain unauthorized access to systems.",
            hindiAnswer =
                "मालवेयर ऐसा सॉफ्टवेयर है जिसे सिस्टम को नुकसान पहुँचाने, बाधित करने, निगरानी करने या अनधिकृत पहुँच पाने के लिए बनाया जाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is qr code",
                "what is qr"
            ),
            englishAnswer =
                "A QR code is a two-dimensional barcode that can store and encode information.",
            hindiAnswer =
                "QR code एक दो-आयामी barcode है जिसमें जानकारी को encode किया जा सकता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is mp3"
            ),
            englishAnswer =
                "MP3 is a widely used digital audio format that uses lossy compression.",
            hindiAnswer =
                "MP3 एक व्यापक रूप से उपयोग किया जाने वाला digital audio format है जो lossy compression का उपयोग करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is microphone"
            ),
            englishAnswer =
                "A microphone converts sound waves into an electrical or digital signal.",
            hindiAnswer =
                "माइक्रोफोन ध्वनि तरंगों को विद्युत या डिजिटल signal में बदलता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is speaker"
            ),
            englishAnswer =
                "A speaker converts an audio signal into sound.",
            hindiAnswer =
                "स्पीकर audio signal को ध्वनि में बदलता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is image recognition"
            ),
            englishAnswer =
                "Image recognition is the process of analyzing an image to determine what it contains.",
            hindiAnswer =
                "इमेज recognition किसी चित्र का विश्लेषण करके उसमें मौजूद चीजों को निर्धारित करने की प्रक्रिया है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is face recognition"
            ),
            englishAnswer =
                "Face recognition is a computer-vision technique used to compare or identify faces using measurable facial features.",
            hindiAnswer =
                "फेस recognition computer-vision तकनीक है जो मापने योग्य चेहरे की विशेषताओं के आधार पर चेहरों की तुलना या पहचान करती है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is object detection"
            ),
            englishAnswer =
                "Object detection is a computer-vision task that identifies objects and their locations in an image.",
            hindiAnswer =
                "Object detection computer-vision का कार्य है जो चित्र में वस्तुओं और उनके स्थानों को पहचानता है।"
        ),

        // ------------------------------------------------------------
        // NATURE / EARTH
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "largest ocean",
                "biggest ocean"
            ),
            englishAnswer =
                "The Pacific Ocean is the largest ocean on Earth.",
            hindiAnswer =
                "प्रशांत महासागर पृथ्वी का सबसे बड़ा महासागर है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "highest mountain",
                "tallest mountain"
            ),
            englishAnswer =
                "Mount Everest is the highest mountain above sea level.",
            hindiAnswer =
                "माउंट एवरेस्ट समुद्र तल से सबसे ऊँचा पर्वत है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "largest continent",
                "biggest continent"
            ),
            englishAnswer =
                "Asia is the largest continent by area.",
            hindiAnswer =
                "एशिया क्षेत्रफल के आधार पर सबसे बड़ा महाद्वीप है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "smallest continent"
            ),
            englishAnswer =
                "Australia is the smallest continent by area.",
            hindiAnswer =
                "ऑस्ट्रेलिया क्षेत्रफल के आधार पर सबसे छोटा महाद्वीप है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how many continents",
                "number of continents"
            ),
            englishAnswer =
                "There are seven continents.",
            hindiAnswer =
                "सात महाद्वीप हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "how many oceans",
                "number of oceans"
            ),
            englishAnswer =
                "There are five commonly recognized oceans.",
            hindiAnswer =
                "आमतौर पर पाँच महासागर माने जाते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is climate"
            ),
            englishAnswer =
                "Climate describes long-term patterns of weather in a region.",
            hindiAnswer =
                "जलवायु किसी क्षेत्र में लंबे समय के मौसम के पैटर्न को बताती है।"
        ),


        OfflineQuestion(
            keywords = listOf(
                "what is earthquake"
            ),
            englishAnswer =
                "An earthquake is the shaking of the ground caused by sudden movement within Earth's crust or upper mantle.",
            hindiAnswer =
                "भूकंप पृथ्वी की पपड़ी या ऊपरी मेंटल के भीतर अचानक गति के कारण जमीन का हिलना है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is volcano"
            ),
            englishAnswer =
                "A volcano is a geological structure through which molten rock, ash, or gases can reach the surface.",
            hindiAnswer =
                "ज्वालामुखी एक भूवैज्ञानिक संरचना है जिसके माध्यम से पिघली चट्टान, राख या गैसें सतह तक पहुँच सकती हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is tsunami"
            ),
            englishAnswer =
                "A tsunami is a series of large sea waves usually caused by major displacement of seawater.",
            hindiAnswer =
                "सुनामी समुद्र के पानी के बड़े विस्थापन से उत्पन्न होने वाली बड़ी समुद्री लहरों की श्रृंखला है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is rainbow"
            ),
            englishAnswer =
                "A rainbow is an optical phenomenon produced when light is refracted, reflected, and dispersed by water droplets.",
            hindiAnswer =
                "इंद्रधनुष एक प्रकाशीय घटना है जो पानी की बूंदों द्वारा प्रकाश के अपवर्तन, परावर्तन और विक्षेपण से बनता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is plant"
            ),
            englishAnswer =
                "A plant is a living organism that generally produces its own food through photosynthesis.",
            hindiAnswer =
                "पौधा एक जीवित organism है जो सामान्यतः प्रकाश संश्लेषण के माध्यम से अपना भोजन बनाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is animal"
            ),
            englishAnswer =
                "An animal is a multicellular living organism belonging to the animal kingdom.",
            hindiAnswer =
                "जानवर बहुकोशिकीय जीवित organism है जो प्राणी जगत से संबंधित होता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is ecosystem"
            ),
            englishAnswer =
                "An ecosystem includes living organisms and the nonliving environment interacting in an area.",
            hindiAnswer =
                "पारिस्थितिकी तंत्र में किसी क्षेत्र के जीवित organisms और निर्जीव पर्यावरण के बीच की पारस्परिक क्रिया शामिल होती है।"
        ),

        // ------------------------------------------------------------
        // HUMAN BODY
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "how many bones in human body",
                "number of bones in human body"
            ),
            englishAnswer =
                "A typical adult human skeleton has 206 bones.",
            hindiAnswer =
                "एक सामान्य वयस्क मानव कंकाल में 206 हड्डियाँ होती हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "largest organ in human body",
                "biggest organ in human body"
            ),
            englishAnswer =
                "The skin is the largest organ of the human body by surface area.",
            hindiAnswer =
                "त्वचा सतह क्षेत्र के आधार पर मानव शरीर का सबसे बड़ा अंग है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is heart"
            ),
            englishAnswer =
                "The heart is a muscular organ that pumps blood through the circulatory system.",
            hindiAnswer =
                "हृदय एक मांसपेशीय अंग है जो परिसंचरण तंत्र में रक्त पंप करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is brain"
            ),
            englishAnswer =
                "The brain is the main organ of the nervous system and coordinates many body functions.",
            hindiAnswer =
                "मस्तिष्क तंत्रिका तंत्र का मुख्य अंग है और शरीर के कई कार्यों का समन्वय करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is blood"
            ),
            englishAnswer =
                "Blood is a connective tissue that transports oxygen, nutrients, hormones, and waste products.",
            hindiAnswer =
                "रक्त एक connective tissue है जो ऑक्सीजन, पोषक तत्व, हार्मोन और अपशिष्ट पदार्थों का परिवहन करता है।"
        ),

        // ------------------------------------------------------------
        // SPACE ORGANIZATIONS / ENGINEERING
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "what is isro"
            ),
            englishAnswer =
                "ISRO is the Indian Space Research Organisation.",
            hindiAnswer =
                "ISRO का अर्थ Indian Space Research Organisation है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is nasa"
            ),
            englishAnswer =
                "NASA is the United States government agency responsible for the nation's civil space program and aeronautics research.",
            hindiAnswer =
                "NASA संयुक्त राज्य अमेरिका की सरकारी एजेंसी है जो नागरिक अंतरिक्ष कार्यक्रम और एयरोनॉटिक्स अनुसंधान के लिए जिम्मेदार है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is rocket"
            ),
            englishAnswer =
                "A rocket is a vehicle or engine that generates thrust by expelling mass.",
            hindiAnswer =
                "रॉकेट एक वाहन या इंजन है जो पदार्थ को बाहर निकालकर thrust उत्पन्न करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is orbit"
            ),
            englishAnswer =
                "An orbit is the curved path of an object around another object under the influence of gravity.",
            hindiAnswer =
                "कक्षा वह घुमावदार मार्ग है जिसमें कोई वस्तु गुरुत्वाकर्षण के प्रभाव में किसी अन्य वस्तु की परिक्रमा करती है।"
        ),

        // ------------------------------------------------------------
        // MATH / UNITS
        // ------------------------------------------------------------

        OfflineQuestion(
            keywords = listOf(
                "what is kilogram"
            ),
            englishAnswer =
                "A kilogram is the SI base unit of mass.",
            hindiAnswer =
                "किलोग्राम द्रव्यमान की SI मूल इकाई है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is meter"
            ),
            englishAnswer =
                "A meter is the SI base unit of length.",
            hindiAnswer =
                "मीटर लंबाई की SI मूल इकाई है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is second"
            ),
            englishAnswer =
                "A second is the SI base unit of time.",
            hindiAnswer =
                "सेकंड समय की SI मूल इकाई है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is percentage"
            ),
            englishAnswer =
                "A percentage expresses a value as a fraction of one hundred.",
            hindiAnswer =
                "प्रतिशत किसी मान को सौ के अंश के रूप में व्यक्त करता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is fraction"
            ),
            englishAnswer =
                "A fraction represents a part of a whole using a numerator and denominator.",
            hindiAnswer =
                "भिन्न किसी पूर्ण वस्तु के एक भाग को numerator और denominator की सहायता से दर्शाता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is prime number",
                "what are prime numbers"
            ),
            englishAnswer =
                "A prime number is a whole number greater than 1 with exactly two positive divisors: 1 and itself.",
            hindiAnswer =
                "अभाज्य संख्या 1 से बड़ी पूर्ण संख्या है जिसके ठीक दो धनात्मक भाजक होते हैं: 1 और स्वयं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is zero"
            ),
            englishAnswer =
                "Zero is the integer representing no quantity and is neither positive nor negative.",
            hindiAnswer =
                "शून्य ऐसी पूर्ण संख्या है जो कोई मात्रा न होने को दर्शाती है और न धनात्मक है न ऋणात्मक।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is triangle"
            ),
            englishAnswer =
                "A triangle is a polygon with three sides.",
            hindiAnswer =
                "त्रिभुज तीन भुजाओं वाला बहुभुज है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is square"
            ),
            englishAnswer =
                "A square is a quadrilateral with four equal sides and four right angles.",
            hindiAnswer =
                "वर्ग चार बराबर भुजाओं और चार समकोणों वाला चतुर्भुज है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is circle"
            ),
            englishAnswer =
                "A circle is a set of points in a plane that are all the same distance from a center point.",
            hindiAnswer =
                "वृत्त समतल में उन बिंदुओं का समूह है जो एक केंद्र बिंदु से समान दूरी पर होते हैं।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is angle"
            ),
            englishAnswer =
                "An angle is formed by two rays or line segments meeting at a common point.",
            hindiAnswer =
                "कोण दो किरणों या रेखाखंडों के एक सामान्य बिंदु पर मिलने से बनता है।"
        ),

        OfflineQuestion(
            keywords = listOf(
                "what is pi"
            ),
            englishAnswer =
                "Pi is the mathematical constant approximately equal to 3.14159.",
            hindiAnswer =
                "पाई एक गणितीय स्थिरांक है जिसका मान लगभग 3.14159 है।"
        )
    )

    /*
     * Generic words that should not decide
     * which question the user is asking.
     */
    private val stopWords = setOf(
        "what",
        "is",
        "the",
        "a",
        "an",
        "are",
        "am",
        "was",
        "were",
        "who",
        "how",
        "why",
        "when",
        "where",
        "which",
        "tell",
        "me",
        "about",
        "of",
        "to",
        "for",
        "in",
        "on",
        "and",
        "or",
        "do",
        "does",
        "did",
        "can",
        "could",
        "would",
        "you",
        "your",
        "please",
        "my",
        "i"
    )

    fun find(
        query: String
    ): OfflineQuestion? {

        val normalizedQuery =
            normalize(query)

        if (normalizedQuery.isBlank()) {
            return null
        }

        /*
         * ------------------------------------------------------------
         * PASS 1: EXACT MATCH
         * ------------------------------------------------------------
         *
         * Best possible match.
         */
        for (question in questions) {

            for (keyword in question.keywords) {

                val normalizedKeyword =
                    normalize(keyword)

                if (
                    normalizedQuery ==
                    normalizedKeyword
                ) {
                    return question
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * PASS 2: EXACT PHRASE INSIDE USER SENTENCE
         * ------------------------------------------------------------
         *
         * Example:
         * "please tell me what is a black hole"
         *
         * contains:
         * "what is a black hole"
         */
        for (question in questions) {

            for (keyword in question.keywords) {

                val normalizedKeyword =
                    normalize(keyword)

                if (
                    normalizedKeyword.length >= 4 &&
                    normalizedQuery.contains(
                        normalizedKeyword
                    )
                ) {
                    return question
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * PASS 3: MEANINGFUL WORD MATCHING
         * ------------------------------------------------------------
         *
         * Generic words such as "what" and "is"
         * are ignored.
         */
        var bestQuestion: OfflineQuestion? =
            null

        var bestScore = 0

        for (question in questions) {

            for (keyword in question.keywords) {

                val normalizedKeyword =
                    normalize(keyword)

                val keywordWords =
                    meaningfulWords(normalizedKeyword)

                val score =
                    meaningfulWordScore(
                        normalizedQuery,
                        normalizedKeyword
                    )

                /*
                 * IMPORTANT: Do not let a generic single word such as
                 * "capital", "national", "country", or "planet" select
                 * the wrong database entry.
                 *
                 * For a multi-word keyword, at least TWO meaningful words
                 * must match. This means:
                 *
                 *   "capital of Bangladesh"
                 *
                 * can match the Bangladesh entry because both
                 * "capital" and "bangladesh" match, but it cannot match
                 * the India entry merely because "capital" matches.
                 *
                 * Single-word keywords are still allowed to match normally
                 * so entries such as "bluetooth", "photosynthesis", or
                 * "hello" continue to work.
                 */
                val minimumMatchedWords =
                    if (keywordWords.size >= 2) {
                        2
                    } else {
                        1
                    }

                val matchedWords =
                    matchedMeaningfulWordCount(
                        normalizedQuery,
                        normalizedKeyword
                    )

                if (matchedWords < minimumMatchedWords) {
                    continue
                }

                if (score > bestScore) {

                    bestScore = score
                    bestQuestion = question
                }
            }
        }

        /*
         * Require a meaningful multi-word match.
         *
         * A score of 100 means every meaningful keyword word matched.
         * A lower score is allowed when the database keyword contains
         * additional optional wording, but at least two meaningful words
         * must already have matched for a multi-word keyword above.
         */
        return if (bestQuestion != null && bestScore >= 50) {
            bestQuestion
        } else {
            null
        }
    }

    private fun meaningfulWordScore(
        query: String,
        keyword: String
    ): Int {

        val queryWords =
            meaningfulWords(query)

        val keywordWords =
            meaningfulWords(keyword)

        if (
            queryWords.isEmpty() ||
            keywordWords.isEmpty()
        ) {
            return 0
        }

        val matchedWords =
            matchedMeaningfulWordCount(
                query,
                keyword
            )

        if (matchedWords == 0) {
            return 0
        }

        return (
                matchedWords * 100
                ) / keywordWords.size
    }

    private fun matchedMeaningfulWordCount(
        query: String,
        keyword: String
    ): Int {

        val queryWords =
            meaningfulWords(query)

        val keywordWords =
            meaningfulWords(keyword)

        if (
            queryWords.isEmpty() ||
            keywordWords.isEmpty()
        ) {
            return 0
        }

        var matchedWords = 0

        for (keywordWord in keywordWords) {

            if (queryWords.contains(keywordWord)) {
                matchedWords++
            }
        }

        return matchedWords
    }

    private fun meaningfulWords(
        text: String
    ): Set<String> {

        return text
            .split(" ")
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .filter {
                it !in stopWords
            }
            .filter {
                it.length >= 2
            }
            .toSet()
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("[^\\p{L}\\p{N}\\s]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }
}