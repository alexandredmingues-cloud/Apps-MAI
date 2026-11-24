package com.example.data

import com.example.data.model.BadgeLevel
import com.example.data.model.ClientDossier
import com.example.data.model.CompatibilityBadge
import com.example.data.model.CompatibilityResult
import com.example.data.model.FinanceMetrics
import com.example.data.model.HcsfCapacityInput
import com.example.data.model.HcsfCapacityResult
import com.example.data.model.HcsfDurationScenario
import com.example.data.model.MontpellierSector
import com.example.data.model.OfferRecord
import com.example.data.model.PropertyFeedback
import com.example.data.model.PropertyItem
import com.example.data.model.TimelineEvent
import com.example.data.model.VisitRecord
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object MontyMarketData {
    val SECTEURS_MONTPELLIER = listOf(
        MontpellierSector(
            name = "Croix d'Argent (34070)",
            priceM2Apartment = 3250,
            priceM2House = 3890,
            evolution = "+2.4%",
            tension = "Forte",
            avgDpe = "C",
            description = "Quartier résidentiel recherché, tramway L2, proximité immédiate centre et accès A709."
        ),
        MontpellierSector(
            name = "Mas Drevon (34070)",
            priceM2Apartment = 3180,
            priceM2House = 3750,
            evolution = "+2.2%",
            tension = "Forte",
            avgDpe = "C",
            description = "Secteur calme et arboré à proximité immédiate du Tram L2 et des parcs."
        ),
        MontpellierSector(
            name = "Ovalie (34070)",
            priceM2Apartment = 3780,
            priceM2House = 4250,
            evolution = "+3.1%",
            tension = "Très forte",
            avgDpe = "B",
            description = "Résidences récentes de standing autour du GGL Stadium, cadre verdoyant et familial."
        ),
        MontpellierSector(
            name = "Port Marianne (34000)",
            priceM2Apartment = 4850,
            priceM2House = 5400,
            evolution = "+1.8%",
            tension = "Très forte",
            avgDpe = "B",
            description = "Architecture contemporaine signée, berges du Lez, bassins Jacques Cœur, tram L1/L3."
        ),
        MontpellierSector(
            name = "Antigone (34000)",
            priceM2Apartment = 4120,
            priceM2House = null,
            evolution = "+1.5%",
            tension = "Forte",
            avgDpe = "C",
            description = "Architecture néoclassique Ricardo Bofill, hyper-connecté, idéal patrimonial et familial."
        ),
        MontpellierSector(
            name = "Les Arceaux (34000)",
            priceM2Apartment = 4490,
            priceM2House = 5200,
            evolution = "+2.9%",
            tension = "Extrême",
            avgDpe = "D",
            description = "Charme de l'ancien, marché bio de l'aqueduc Saint-Clément, esprit village."
        ),
        MontpellierSector(
            name = "Hôpitaux-Facultés (34090)",
            priceM2Apartment = 3650,
            priceM2House = 4380,
            evolution = "+2.1%",
            tension = "Forte",
            avgDpe = "C",
            description = "Secteur calme et boisé, forte demande familiale et médicale, tram L1."
        ),
        MontpellierSector(
            name = "Juvignac (34990)",
            priceM2Apartment = 3320,
            priceM2House = 3950,
            evolution = "+3.6%",
            tension = "Forte",
            avgDpe = "B",
            description = "Golf de Fontcaude, Les Constellations, cadre nature à 15 min du centre."
        ),
        MontpellierSector(
            name = "Baillargues (34670)",
            priceM2Apartment = 3480,
            priceM2House = 4190,
            evolution = "+4.2%",
            tension = "Très forte",
            avgDpe = "C",
            description = "Commune prisée à l'Est, gare TER (7 min St-Roch), golf Massane, écoles réputées."
        )
    )

    val OPTIONS_CRITERES_ESSENTIELS = listOf(
        "Terrasse / Extérieur",
        "Parking / Garage",
        "Calme absolu",
        "Ascenseur",
        "Proche Tramway",
        "Dernier étage",
        "DPE A, B ou C",
        "3 chambres minimum"
    )

    val OPTIONS_TYPES_BIEN = listOf(
        "Appartement",
        "Maison",
        "Villa",
        "Rez-de-jardin"
    )

    val ETAPES_PROJET = listOf(
        "Découverte",
        "Sélection",
        "Visites",
        "Offre d'achat",
        "Compromis",
        "Acte Notaire"
    )

    fun initialClients(uid: String): List<Pair<String, ClientDossier>> = listOf(
        "cli-1" to ClientDossier(
            id = "cli-1",
            userId = uid,
            name = "Laurent — Claire & Thomas",
            phone = "06 42 18 90 34",
            email = "thomas.laurent34@example.fr",
            situation = "Couple marié, 2 enfants (6 et 9 ans) — Mutation CHU Lapeyronie / Cadre aéronautique",
            status = "Actif — Visites en cours",
            projectStage = 2L,
            budgetMax = 445000L,
            downPayment = 95000L,
            monthlyIncome = 6800L,
            existingCredits = 0L,
            bankApproval = "Accord de principe Caisse d'Épargne Languedoc (taux 3.35% sur 25 ans)",
            sectors = listOf("Croix d'Argent (34070)", "Ovalie (34070)", "Juvignac (34990)"),
            propertyType = "Appartement",
            minSurface = 88L,
            minRooms = 3L,
            essentialCriteria = listOf("Terrasse / Extérieur", "Parking / Garage", "DPE A, B ou C", "3 chambres minimum"),
            secondaryCriteria = listOf("Proche Tramway", "Calme absolu"),
            timelineDelai = "Sous 3 mois (rentrée scolaire)",
            clientMessageToAdvisor = "Nous privilégions un environnement familial proche des écoles et du Tram L2.",
            criteriaModifiedByClient = false,
            lastClientUpdateLabel = null,
            selectedPropertyIds = listOf("prop-1", "prop-2"),
            propertyFeedback = mapOf(
                "prop-1" to PropertyFeedback(5L, "Coup de cœur", "Terrasse plein Sud et calme très appréciés lors de la visite."),
                "prop-2" to PropertyFeedback(4L, "Intéressant", "Le jardin est un vrai plus, à voir pour l'aménagement de la salle d'eau.")
            ),
            portalActive = true,
            portalToken = "MNT-LRNT-8492-X7",
            portalExpiration = "2026-12-31",
            privateNotes = "Acheteurs très sérieux et solvables. Thomas est sensible aux charges de copropriété et à la performance thermique. Claire a eu un vrai coup de cœur sur la luminosité d'Ovalie mais craint le vis-à-vis.",
            timeline = listOf(
                TimelineEvent("18 Nov 2026", "visite", "Visite T4 Terrasse Ovalie — Résidence Le XV", "Note 8.5/10. Très bon ressenti global, vérification PV d'AG 2025."),
                TimelineEvent("12 Nov 2026", "selection", "Envoi sélection de 3 biens qualifiés", "Partage sur le portail client sécurisé."),
                TimelineEvent("05 Nov 2026", "finance", "Validation enveloppe financière", "Budget validé à 445 000 € FAI avec 95 000 € d'apport."),
                TimelineEvent("29 Oct 2026", "contact", "Premier entretien découverte — Agence Montpellier Sud", "Définition du cahier des charges complet.")
            )
        ),
        "cli-2" to ClientDossier(
            id = "cli-2",
            userId = uid,
            name = "Moreau — Sophie & Marc",
            phone = "06 79 51 22 10",
            email = "sophie.moreau.arch@example.fr",
            situation = "Profession libérale & dirigeant PME — Résidence principale patrimoniale",
            status = "Offre en cours",
            projectStage = 3L,
            budgetMax = 690000L,
            downPayment = 210000L,
            monthlyIncome = 11200L,
            existingCredits = 450L,
            bankApproval = "Financement privé BNP Banque Privée validé",
            sectors = listOf("Port Marianne (34000)", "Les Arceaux (34000)", "Antigone (34000)"),
            propertyType = "Appartement",
            minSurface = 110L,
            minRooms = 3L,
            essentialCriteria = listOf("Terrasse / Extérieur", "Parking / Garage", "Ascenseur", "Dernier étage"),
            secondaryCriteria = listOf("Calme absolu", "Proche Tramway"),
            timelineDelai = "Immédiat sur coup de cœur",
            clientMessageToAdvisor = "Recherche d'un bien architectural avec grande terrasse plein ciel.",
            selectedPropertyIds = listOf("prop-3"),
            propertyFeedback = mapOf(
                "prop-3" to PropertyFeedback(5L, "Coup de cœur", "Offre déposée à 645 000 €, en attente du retour vendeur.")
            ),
            portalActive = true,
            portalToken = "MNT-MORE-3310-K9",
            portalExpiration = "2027-01-15",
            privateNotes = "Clients exigeants habitués aux prestations haut de gamme. Offre rédigée à 645 000 € sur le T4 Port Marianne (affiché 675 000 €).",
            timeline = listOf(
                TimelineEvent("18 Nov 2026", "offre", "Offre d'achat transmise — 645 000 €", "Offre argumentée sur le T4 Port Marianne Bassin Jacques Cœur."),
                TimelineEvent("14 Nov 2026", "visite", "Contre-visite avec architecte d'intérieur", "Validation du potentiel d'aménagement suite parentale.")
            )
        ),
        "cli-3" to ClientDossier(
            id = "cli-3",
            userId = uid,
            name = "Benali — Dr. Karim Benali",
            phone = "07 83 14 66 91",
            email = "k.benali.med@example.fr",
            situation = "Praticien hospitalier — Recherche maison familiale calme",
            status = "Nouveau prospect qualifié",
            projectStage = 1L,
            budgetMax = 580000L,
            downPayment = 130000L,
            monthlyIncome = 8900L,
            existingCredits = 0L,
            bankApproval = "Simulation courtier validée (capacité jusqu'à 600k€)",
            sectors = listOf("Baillargues (34670)", "Hôpitaux-Facultés (34090)", "Juvignac (34990)"),
            propertyType = "Villa",
            minSurface = 115L,
            minRooms = 4L,
            essentialCriteria = listOf("Calme absolu", "DPE A, B ou C", "Parking / Garage", "Terrasse / Extérieur"),
            secondaryCriteria = listOf("Proche Tramway"),
            timelineDelai = "4 à 6 mois",
            clientMessageToAdvisor = "Besoin d'un accès fluide vers le CHU Lapeyronie.",
            selectedPropertyIds = listOf("prop-4"),
            propertyFeedback = mapOf(
                "prop-4" to PropertyFeedback(5L, "Coup de cœur", "La suite parentale au RDC et la proximité TER sont idéales.")
            ),
            portalActive = true,
            portalToken = "MNT-BENL-9021-P2",
            portalExpiration = "2026-12-20",
            privateNotes = "A besoin d'être rassuré sur les temps de trajet vers Lapeyronie aux heures de pointe.",
            timeline = listOf(
                TimelineEvent("16 Nov 2026", "selection", "Ajout Villa Baillargues Golf dans sa sélection", "Compatibilité estimée à 92/100."),
                TimelineEvent("10 Nov 2026", "contact", "Qualification téléphonique & création espace", "Envoi du lien d'accès portail.")
            )
        )
    )

    fun initialProperties(uid: String): List<Pair<String, PropertyItem>> = listOf(
        "prop-1" to PropertyItem(
            id = "prop-1",
            userId = uid,
            title = "T4 Lumineux & Terrasse Sud — Résidence Le XV",
            sector = "Ovalie (34070)",
            address = "Avenue du XV de France, 34070 Montpellier",
            type = "Appartement",
            price = 429000L,
            surface = 94L,
            rooms = 4L,
            bedrooms = 3L,
            floor = "3ème / 4 avec ascenseur",
            dpe = "B",
            ges = "A",
            monthlyCharges = 165L,
            propertyTax = 1890L,
            estimatedWorks = 0L,
            listingStatus = "Vérifiée — Mandat Exclusif",
            source = "vérifiée",
            exterior = "Terrasse 22 m² exposée Sud-Ouest",
            parking = "Double box fermé en sous-sol",
            environment = "Calme, vue dégagée sur parc arboré, Tram L2 Sabines à 8 min à pied.",
            transports = "Tram L2 (Sabines), Bus 15, Accès A709 à 4 min",
            schoolsShops = "Groupe scolaire Cheng-Du à 400m, commerces Ovalie au pied",
            generalCondition = "Excellent (construction 2019, aucun travaux)",
            strengths = listOf("DPE B très économe", "Terrasse profonde 22m² sans vis-à-vis", "Double box fermé électrifié", "Garantie décennale en cours"),
            weaknesses = listOf("Taxe foncière de 1 890 €/an", "Cuisine semi-ouverte (cloison non porteuse ouvrable)"),
            description = "Au cœur du quartier prisé d'Ovalie, superbe appartement T4 de 94 m² en étage élevé offrant une pièce de vie de 38 m² baignée de lumière prolongée par une terrasse de 22 m² au calme absolu.",
            mandateRef = "MND-2026-041",
            mandateType = "Exclusif",
            mandateDate = "2026-10-18",
            agencyFeePercent = 4.5,
            listingUrl = "https://www.seloger.com/annonces/achat/appartement/montpellier-34/ovalie/example-prop1.htm",
            listingPortal = "SeLoger",
            dvfEstimatedEuro = 425000L,
            dvfEstimatedLowEuro = 402000L,
            dvfEstimatedHighEuro = 448000L,
            dvfScore = 84,
            dvfCriteriaSummary = "Étage avec ascenseur • Vue dégagée • Terrasse ≥ 15 m² • Double box",
            advisorValuationNotes = "Prix affiché parfaitement aligné sur les ventes notariales DVF du XV de France.",
            latitude = 43.5936,
            longitude = 3.8467
        ),
        "prop-2" to PropertyItem(
            id = "prop-2",
            userId = uid,
            title = "Maison de Ville Contemporaine avec Jardin",
            sector = "Croix d'Argent (34070)",
            address = "Rue des Chênes Verts, 34070 Montpellier",
            type = "Maison",
            price = 448000L,
            surface = 102L,
            rooms = 4L,
            bedrooms = 3L,
            floor = "R+1",
            dpe = "C",
            ges = "B",
            monthlyCharges = 35L,
            propertyTax = 2050L,
            estimatedWorks = 8500L,
            listingStatus = "Vérifiée — Mandat Simple",
            source = "vérifiée",
            exterior = "Jardin clos et arboré de 65 m² + terrasse bois",
            parking = "Garage + 1 place privative",
            environment = "Impasse résidentielle très calme à 300m du Parc Montcalm.",
            transports = "Tram L2 (Croix d'Argent) à 5 min à pied",
            schoolsShops = "Écoles, marché et commerces de proximité à 3 min",
            generalCondition = "Très bon état, rafraîchissement salle d'eau à prévoir",
            strengths = listOf("Aucune copropriété lourde (ASL 35€/mois)", "Proximité immédiate Parc Montcalm et Tram L2", "Vrai jardin intimiste au calme"),
            weaknesses = listOf("Rafraîchissement salle d'eau étage (~8 500 €)", "Chambres à l'étage mansardées sur 1 pan"),
            description = "Rare sur Croix d'Argent : maison de ville de 102 m² habitable avec jardin intimiste sans vis-à-vis, séjour traversant Est-Ouest, 3 chambres à l'étage et garage attenant.",
            mandateRef = "MND-2026-048",
            mandateType = "Simple",
            mandateDate = "2026-10-25",
            agencyFeePercent = 4.2,
            listingUrl = "https://www.bienici.com/annonce/vente/montpellier/maison/example-prop2",
            listingPortal = "Bien'ici",
            latitude = 43.5908,
            longitude = 3.8614
        ),
        "prop-3" to PropertyItem(
            id = "prop-3",
            userId = uid,
            title = "T4 Dernier Étage — Bassin Jacques Cœur",
            sector = "Port Marianne (34000)",
            address = "Avenue Raymond Dugrand, 34000 Montpellier",
            type = "Appartement",
            price = 675000L,
            surface = 118L,
            rooms = 4L,
            bedrooms = 3L,
            floor = "6ème / 6 avec ascenseur",
            dpe = "B",
            ges = "A",
            monthlyCharges = 240L,
            propertyTax = 2490L,
            estimatedWorks = 0L,
            listingStatus = "Vérifiée — Offre en cours",
            source = "vérifiée",
            exterior = "Terrasse panoramique 42 m² vue dégagée",
            parking = "Double garage fermé en sous-sol sécurisé",
            environment = "Résidence d'architecte iconique, commerces haut de gamme et berges du Lez.",
            transports = "Tram L1 et L3 (Port Marianne) à 2 min",
            schoolsShops = "Restauration, marché du Lez, centre médical à pied",
            generalCondition = "Prestations luxe (parquet massif, gainable, domotique)",
            strengths = listOf("Dernier étage avec terrasse 42 m²", "Signature architecturale recherchée", "Climatisation gainable intégrée"),
            weaknesses = listOf("Charges de copropriété 240 €/mois", "Prix affiché dans le haut de la fourchette"),
            description = "Appartement d'exception au dernier étage d'une résidence emblématique de Port Marianne. Séjour cathédrale de 48 m², suite parentale, 2 chambres et terrasse plein ciel de 42 m².",
            mandateRef = "MND-2026-052",
            mandateType = "Exclusif",
            mandateDate = "2026-11-02",
            agencyFeePercent = 4.0,
            listingUrl = "https://www.leboncoin.fr/ad/ventes_immobilieres/example-prop3.htm",
            listingPortal = "Leboncoin",
            dvfEstimatedEuro = 652000L,
            dvfEstimatedLowEuro = 620000L,
            dvfEstimatedHighEuro = 684000L,
            dvfScore = 88,
            dvfCriteriaSummary = "Dernier étage + asc. • Vue dégagée • Terrasse 42 m²",
            advisorValuationNotes = "Surcote dernier étage justifiée ; marge de négo réaliste autour de 645 000 €.",
            latitude = 43.6003,
            longitude = 3.9041
        ),
        "prop-4" to PropertyItem(
            id = "prop-4",
            userId = uid,
            title = "Villa Familiale Contemporaine — Secteur Golf",
            sector = "Baillargues (34670)",
            address = "Allée des Pins, 34670 Baillargues",
            type = "Villa",
            price = 565000L,
            surface = 132L,
            rooms = 5L,
            bedrooms = 4L,
            floor = "R+1",
            dpe = "B",
            ges = "A",
            monthlyCharges = 0L,
            propertyTax = 2150L,
            estimatedWorks = 0L,
            listingStatus = "Vérifiée — Mandat Exclusif",
            source = "vérifiée",
            exterior = "Terrain paysager 380 m² avec piscine chauffée",
            parking = "Garage isolé 22 m² + 2 stationnements",
            environment = "Quartier résidentiel prisé, calme absolu, piste cyclable.",
            transports = "Gare TER Baillargues à 4 min (Montpellier Saint-Roch en 7 min)",
            schoolsShops = "Écoles, collège et centre village à 5 min",
            generalCondition = "Parfait état (normes RT2012, pompe à chaleur)",
            strengths = listOf("Suite parentale de plain-pied", "Piscine maçonnée avec volet immergé", "Accès gare TER ultra-rapide"),
            weaknesses = listOf("Desserte tramway indirecte (via TER)"),
            description = "Superbe villa 5 pièces de 132 m² sur parcelle paysagée de 380 m² avec piscine. Pièce de vie de 52 m² ouverte sur terrasse bioclimatique, suite parentale au RDC et 3 chambres à l'étage.",
            mandateRef = "MND-2026-063",
            mandateType = "Exclusif",
            mandateDate = "2026-11-05",
            agencyFeePercent = 4.5,
            listingUrl = "https://www.seloger.com/annonces/achat/maison/baillargues-34/example-prop4.htm",
            listingPortal = "SeLoger",
            latitude = 43.6606,
            longitude = 4.0132
        ),
        "prop-5" to PropertyItem(
            id = "prop-5",
            userId = uid,
            title = "T4 Familial Rénové — Les Constellations",
            sector = "Juvignac (34990)",
            address = "Allée de l'Europe, 34990 Juvignac",
            type = "Appartement",
            price = 369000L,
            surface = 91L,
            rooms = 4L,
            bedrooms = 3L,
            floor = "2ème / 3 avec ascenseur",
            dpe = "C",
            ges = "B",
            monthlyCharges = 145L,
            propertyTax = 1620L,
            estimatedWorks = 0L,
            listingStatus = "Délégation — Confrère",
            source = "manuelle",
            exterior = "Loggia / Terrasse 18 m² vue verdure",
            parking = "2 places privatives en sous-sol",
            environment = "Écoquartier calme, proximité immédiate Tram L3 terminus.",
            transports = "Tram L3 (Juvignac) à 3 min à pied",
            schoolsShops = "Supermarché, pharmacie et école à 200m",
            generalCondition = "Rénové en 2024, cuisine équipée neuve",
            strengths = listOf("Prix très compétitif (4 054 €/m²)", "Proximité immédiate Tram L3", "Aucun travaux à prévoir"),
            weaknesses = listOf("Temps de trajet vers Ovalie/Croix d'Argent ~18 min en voiture"),
            description = "Appartement T4 clé en main au cœur des Constellations à Juvignac. Belle pièce de vie lumineuse, 3 chambres au calme, terrasse abritée de 18 m² et double stationnement.",
            mandateRef = "DEL-2026-019",
            mandateType = "Délégation / Confrère",
            mandateDate = "2026-11-08",
            agencyFeePercent = 5.0,
            latitude = 43.6132,
            longitude = 3.8104
        ),
        "prop-6" to PropertyItem(
            id = "prop-6",
            userId = uid,
            title = "Rez-de-Jardin Arboré T4 — Parc Montcalm",
            sector = "Mas Drevon (34070)",
            address = "Rue du Mas Drevon, 34070 Montpellier",
            type = "Rez-de-jardin",
            price = 398000L,
            surface = 89L,
            rooms = 4L,
            bedrooms = 3L,
            floor = "RDC surélevé / 3 avec ascenseur",
            dpe = "B",
            ges = "A",
            monthlyCharges = 155L,
            propertyTax = 1740L,
            estimatedWorks = 0L,
            listingStatus = "Vérifiée — Mandat Semi-exclusif",
            source = "vérifiée",
            exterior = "Terrasse 18 m² + Jardin privatif 55 m² plein Sud",
            parking = "Box fermé en sous-sol + place extérieure",
            environment = "Résidence sécurisée au calme à deux pas du Parc Montcalm.",
            transports = "Tram L2 (Mas Drevon) à 4 min à pied",
            schoolsShops = "Écoles, crèche et commerces à 300m",
            generalCondition = "État impeccable (livraison 2021)",
            strengths = listOf("Jardin privatif 55 m² sans vis-à-vis", "Tram L2 à 4 min à pied", "DPE B faible consommation"),
            weaknesses = listOf("Résidence en copropriété de 42 lots"),
            description = "Rare appartement T4 en rez-de-jardin offrant la sensation d'une maison : séjour lumineux de 36 m² ouvrant sur terrasse et jardin arboré de 55 m², 3 chambres au calme et double stationnement.",
            mandateRef = "MND-2026-071",
            mandateType = "Semi-exclusif",
            mandateDate = "2026-11-12",
            agencyFeePercent = 4.5,
            listingUrl = "https://www.logic-immo.com/detail-vente-montpellier-example-prop6.htm",
            listingPortal = "Logic-Immo",
            latitude = 43.5974,
            longitude = 3.8648
        )
    )

    fun initialVisits(uid: String): List<Pair<String, VisitRecord>> = listOf(
        "vis-1" to VisitRecord(
            id = "vis-1",
            userId = uid,
            date = "2026-11-18",
            timeSlot = "14:30",
            clientId = "cli-1",
            propertyId = "prop-1",
            status = "Réalisée",
            overallRating = 8.5,
            clientImpressions = "Très bel enthousiasme de Claire et Thomas sur le volume du séjour et l'absence de vis-à-vis sur la terrasse. La proximité de l'école Cheng-Du est un point décisif.",
            positives = "Luminosité Sud-Ouest, DPE B, double box électrifié, calme de la résidence.",
            negatives = "Taxe foncière jugée un peu élevée (1 890 €). Souhaitent ouvrir la cloison cuisine.",
            technicalNotes = "Ouverture cloison non porteuse cuisine (~2 500 €). Aucun gros œuvre.",
            nextAction = "Transmettre les 3 derniers PV d'AG et préparer une stratégie d'offre autour de 415 000 €.",
            sharedWithClient = true
        ),
        "vis-2" to VisitRecord(
            id = "vis-2",
            userId = uid,
            date = "2026-11-20",
            timeSlot = "10:30",
            clientId = "cli-1",
            propertyId = "prop-2",
            status = "Programmée",
            overallRating = null,
            clientImpressions = "",
            positives = "",
            negatives = "",
            technicalNotes = "Vérifier l'état de la toiture et le devis estimatif pour la salle d'eau de l'étage.",
            nextAction = "Visite sur place avec Claire et Thomas — Comparer directement avec le T4 d'Ovalie.",
            sharedWithClient = true
        ),
        "vis-3" to VisitRecord(
            id = "vis-3",
            userId = uid,
            date = "2026-11-21",
            timeSlot = "16:00",
            clientId = "cli-3",
            propertyId = "prop-4",
            status = "Programmée",
            overallRating = null,
            clientImpressions = "",
            positives = "",
            negatives = "",
            technicalNotes = "Présenter l'itinéraire vers le CHU Lapeyronie via gare TER Baillargues + Tram L1.",
            nextAction = "Découverte du bien avec Dr. Benali.",
            sharedWithClient = true
        )
    )

    fun initialOffers(uid: String): List<Pair<String, OfferRecord>> = listOf(
        "off-1" to OfferRecord(
            id = "off-1",
            userId = uid,
            date = "2026-11-18",
            clientId = "cli-2",
            propertyId = "prop-3",
            askingPrice = 675000L,
            offeredPrice = 645000L,
            estimatedWorks = 0L,
            status = "Transmise au vendeur",
            sellerContext = "Mutation professionnelle à Lyon prévue en février 2027 — Vendeur motivé pour sécuriser une vente rapide.",
            clientFinancing = "Apport 210 000 € + Accord de principe BNP Banque Privée (dossier solide sans condition de vente préalable).",
            suspensiveConditions = "Obtention d'un prêt de 485 000 € sur 20 ans au taux max de 3.60% (délai 45 jours).",
            arguments = listOf(
                "Financement bancaire déjà validé par banque privée avec 31% d'apport net.",
                "Alignement sur les dernières ventes notarées DVF du secteur Bassin Jacques Cœur (5 466 €/m²).",
                "Disponibilité des acquéreurs pour signer le compromis sous 10 jours chez le notaire."
            )
        )
    )

    fun computeCompatibility(property: PropertyItem, client: ClientDossier?): CompatibilityResult {
        if (client == null) return CompatibilityResult(0, emptyList(), emptyList(), emptyList())

        var score = 45
        val reasons = mutableListOf<String>()
        val alerts = mutableListOf<String>()
        val badges = mutableListOf<CompatibilityBadge>()

        val totalCostWithWorks = property.price + property.estimatedWorks
        val diffBudget = client.budgetMax - totalCostWithWorks

        when {
            diffBudget >= 15000 -> {
                score += 22
                reasons += "Budget maîtrisé : ${formatEuro(totalCostWithWorks)} (travaux inclus), soit ${formatEuro(diffBudget)} sous le plafond."
                badges += CompatibilityBadge("Budget validé", BadgeLevel.OK)
            }
            diffBudget >= 0 -> {
                score += 15
                reasons += "Enveloppe respectée (${formatEuro(totalCostWithWorks)} pour ${formatEuro(client.budgetMax)} max)."
                badges += CompatibilityBadge("Dans l'enveloppe", BadgeLevel.OK)
            }
            diffBudget >= -20000 -> {
                score += 3
                alerts += "Légèrement au-dessus du budget cible (+${formatEuro(-diffBudget)}), négociable selon contexte vendeur."
                badges += CompatibilityBadge("À négocier", BadgeLevel.WARN)
            }
            else -> {
                score -= 25
                alerts += "Dépassement budgétaire significatif (+${formatEuro(-diffBudget)} au-delà de l'enveloppe)."
                badges += CompatibilityBadge("Hors budget", BadgeLevel.ALERT)
            }
        }

        val sectorPrefix = property.sector.substringBefore(" ").lowercase()
        val matchSector = client.sectors.any { it.lowercase().contains(sectorPrefix) }
        val searchMode = SearchPerimeterMode.fromCode(client.searchMode)
        if (searchMode != SearchPerimeterMode.SECTOR_LIST && (client.searchCenterLat != 0.0 || client.searchCenterLon != 0.0)) {
            val commute = GeoCommuteService.evaluatePropertyAccess(
                originLat = client.searchCenterLat,
                originLon = client.searchCenterLon,
                originLabel = client.searchCenterAddress.ifBlank { "Adresse cible" },
                property = property,
                transportMode = TransportMode.fromCode(client.searchTransportMode),
                searchMode = searchMode,
                maxRadiusKm = client.searchRadiusKm,
                maxTravelMinutes = client.searchMaxTravelMinutes
            )
            if (commute.withinPerimeter) {
                score += 14
                reasons += "Dans le périmètre défini : ${commute.badgeLabel} depuis ${commute.originLabel}."
                badges += CompatibilityBadge(commute.badgeLabel, BadgeLevel.OK)
            } else {
                score -= 14
                alerts += "Hors périmètre cible (${commute.badgeLabel} depuis ${commute.originLabel})."
                badges += CompatibilityBadge(commute.badgeLabel, BadgeLevel.WARN)
            }
        } else if (matchSector) {
            score += 15
            reasons += "Localisation cible validée : ${property.sector}."
            badges += CompatibilityBadge(property.sector.substringBefore(" ("), BadgeLevel.OK)
        } else {
            score -= 8
            alerts += "Secteur (${property.sector}) hors de la liste prioritaire."
            badges += CompatibilityBadge("Secteur secondaire", BadgeLevel.WARN)
        }

        if (client.propertyType.isNotBlank() && property.type.contains(client.propertyType, ignoreCase = true)) {
            score += 8
            badges += CompatibilityBadge(property.type, BadgeLevel.OK)
        }

        if (property.surface >= client.minSurface) {
            score += 8
            reasons += "Surface confortable de ${property.surface} m² (minimum souhaité : ${client.minSurface} m²)."
            badges += CompatibilityBadge("${property.surface} m² ≥ ${client.minSurface} m²", BadgeLevel.OK)
        } else {
            score -= 10
            alerts += "Surface inférieure au critère (${property.surface} m² vs ${client.minSurface} m²)."
            badges += CompatibilityBadge("${property.surface} m²", BadgeLevel.WARN)
        }

        if (property.bedrooms >= client.minRooms) {
            score += 7
            reasons += "${property.bedrooms} chambres disponibles (conforme aux ${client.minRooms} requises)."
            badges += CompatibilityBadge("${property.bedrooms} ch.", BadgeLevel.OK)
        } else {
            score -= 12
            alerts += "Nombre de chambres insuffisant (${property.bedrooms} vs ${client.minRooms} requises)."
            badges += CompatibilityBadge("${property.bedrooms}/${client.minRooms} ch.", BadgeLevel.ALERT)
        }

        val blob = "${property.exterior} ${property.parking} ${property.environment} ${property.transports} ${property.floor} DPE ${property.dpe}".lowercase()
        client.essentialCriteria.forEach { crit ->
            val c = crit.lowercase()
            when {
                c.contains("terrasse") && (blob.contains("terrasse") || blob.contains("jardin")) -> score += 3
                c.contains("parking") && (blob.contains("garage") || blob.contains("box") || blob.contains("stationnement")) -> score += 3
                c.contains("tram") && blob.contains("tram") -> score += 3
                c.contains("calme") && blob.contains("calme") -> score += 3
                c.contains("ascenseur") && blob.contains("ascenseur") -> score += 3
                c.contains("dernier") && blob.contains("dernier") -> score += 4
                c.contains("dpe") && property.dpe in listOf("A", "B", "C") -> score += 3
            }
        }

        if (property.dpe in listOf("A", "B", "C")) {
            reasons += "Excellente performance énergétique (DPE ${property.dpe}) limitant les charges."
        } else {
            alerts += "DPE ${property.dpe} : vigilance sur la consommation énergétique."
        }

        return CompatibilityResult(
            score = max(12, min(99, score)),
            reasons = reasons,
            alerts = alerts,
            badges = badges
        )
    }

    fun computeFinanceMetrics(
        price: Long,
        downPayment: Long,
        notaryRatePercent: Double,
        durationYears: Int,
        interestRatePercent: Double,
        insuranceRatePercent: Double,
        monthlyIncome: Long,
        existingCredits: Long,
        monthlyCharges: Long
    ): FinanceMetrics {
        val notaryFees = (price * (notaryRatePercent / 100.0)).roundToLong()
        val totalProjectCost = price + notaryFees
        val borrowedAmount = max(0L, totalProjectCost - downPayment)

        val monthlyRate = interestRatePercent / 100.0 / 12.0
        val months = durationYears * 12

        val monthlyExclInsurance = if (borrowedAmount <= 0L) 0.0 else if (monthlyRate == 0.0) {
            borrowedAmount.toDouble() / months
        } else {
            (borrowedAmount * monthlyRate) / (1.0 - (1.0 + monthlyRate).pow(-months))
        }

        val monthlyInsurance = (borrowedAmount * (insuranceRatePercent / 100.0)) / 12.0
        val monthlyInclInsurance = monthlyExclInsurance + monthlyInsurance

        val totalCreditCost = max(0.0, monthlyInclInsurance * months - borrowedAmount)
        val totalMonthlyDebt = monthlyInclInsurance + existingCredits
        val debtRatioPercent = if (monthlyIncome > 0L) {
            ((totalMonthlyDebt / monthlyIncome) * 1000.0).roundToLong() / 10.0
        } else 0.0
        val remainderToLive = monthlyIncome - totalMonthlyDebt - monthlyCharges
        val realMonthlyCost = monthlyInclInsurance + monthlyCharges

        fun scenarioForYears(years: Int): Long {
            if (borrowedAmount <= 0L) return 0L
            val m = years * 12
            val base = if (monthlyRate == 0.0) {
                borrowedAmount.toDouble() / m
            } else {
                (borrowedAmount * monthlyRate) / (1.0 - (1.0 + monthlyRate).pow(-m))
            }
            return (base + monthlyInsurance).roundToLong()
        }

        return FinanceMetrics(
            notaryFees = notaryFees,
            totalProjectCost = totalProjectCost,
            borrowedAmount = borrowedAmount,
            monthlyExclInsurance = monthlyExclInsurance.roundToLong(),
            monthlyInsurance = monthlyInsurance.roundToLong(),
            monthlyInclInsurance = monthlyInclInsurance.roundToLong(),
            totalCreditCost = totalCreditCost.roundToLong(),
            debtRatioPercent = debtRatioPercent,
            remainderToLive = remainderToLive.roundToLong(),
            realMonthlyHousingCost = realMonthlyCost.roundToLong(),
            scenario20Years = scenarioForYears(20),
            scenario25Years = scenarioForYears(25)
        )
    }

    fun computeHcsfBorrowingCapacity(input: HcsfCapacityInput): HcsfCapacityResult {
        val income = input.monthlyNetIncome.coerceAtLeast(0L)
        val existingCredits = input.existingMonthlyCredits.coerceAtLeast(0L)
        val downPayment = input.downPayment.coerceAtLeast(0L)
        val years = input.durationYears.coerceIn(5, 30)
        val targetRatio = input.maxDebtRatioPercent.coerceIn(10.0, 45.0)
        val annualInterestRate = input.interestRatePercent.coerceIn(0.0, 15.0)
        val annualInsuranceRate = input.insuranceRatePercent.coerceIn(0.0, 3.0)
        val notaryRate = input.notaryRatePercent.coerceIn(0.0, 15.0)

        val maxMonthlyDebtTotalDouble = income * (targetRatio / 100.0)
        val maxMonthlyLoanPaymentDouble = max(0.0, maxMonthlyDebtTotalDouble - existingCredits)

        val monthlyInterestRate = annualInterestRate / 100.0 / 12.0
        val monthlyInsuranceRate = annualInsuranceRate / 100.0 / 12.0

        fun capacityForDuration(durationYears: Int): Pair<Long, Long> {
            val months = durationYears * 12
            val creditFactor = if (monthlyInterestRate == 0.0) {
                1.0 / months
            } else {
                monthlyInterestRate / (1.0 - (1.0 + monthlyInterestRate).pow(-months))
            }
            val totalFactor = creditFactor + monthlyInsuranceRate
            val borrowed = if (maxMonthlyLoanPaymentDouble <= 0.0 || totalFactor <= 0.0) {
                0L
            } else {
                (maxMonthlyLoanPaymentDouble / totalFactor).roundToLong()
            }
            val totalBudget = borrowed + downPayment
            val netPropertyPrice = if (totalBudget <= 0L) {
                0L
            } else {
                (totalBudget / (1.0 + notaryRate / 100.0)).roundToLong()
            }
            return borrowed to netPropertyPrice
        }

        val (maxBorrowedCapital, maxPropertyPriceNet) = capacityForDuration(years)
        val maxTotalProjectBudget = maxBorrowedCapital + downPayment
        val estimatedNotaryFees = max(0L, maxTotalProjectBudget - maxPropertyPriceNet)
        val months = years * 12
        val totalCreditCost = if (maxBorrowedCapital <= 0L) {
            0L
        } else {
            max(0.0, maxMonthlyLoanPaymentDouble * months - maxBorrowedCapital).roundToLong()
        }

        val actualMonthlyLoanPayment = maxMonthlyLoanPaymentDouble.roundToLong()
        val effectiveDebtRatioPercent = if (income > 0L) {
            (((actualMonthlyLoanPayment + existingCredits).toDouble() / income) * 1000.0).roundToLong() / 10.0
        } else {
            0.0
        }
        val remainderToLive = income - existingCredits - actualMonthlyLoanPayment
        val isHcsfRatioCompliant = targetRatio <= 35.0 && (income == 0L || effectiveDebtRatioPercent <= 35.5)
        val isHcsfDurationCompliant = if (input.isVefaNeuf) years <= 27 else years <= 25

        val scenarios = listOf(15, 20, 25).map { d ->
            val (borrowed, netPrice) = capacityForDuration(d)
            HcsfDurationScenario(
                durationYears = d,
                maxBorrowedCapital = borrowed,
                maxPropertyPriceNet = netPrice,
                isHcsfCompliant = d <= 25
            )
        }

        return HcsfCapacityResult(
            maxMonthlyDebtTotal = maxMonthlyDebtTotalDouble.roundToLong(),
            maxMonthlyLoanPayment = actualMonthlyLoanPayment,
            maxBorrowedCapital = maxBorrowedCapital,
            maxTotalProjectBudget = maxTotalProjectBudget,
            maxPropertyPriceNet = maxPropertyPriceNet,
            estimatedNotaryFees = estimatedNotaryFees,
            totalCreditCost = totalCreditCost,
            effectiveDebtRatioPercent = effectiveDebtRatioPercent,
            remainderToLive = remainderToLive,
            isHcsfRatioCompliant = isHcsfRatioCompliant,
            isHcsfDurationCompliant = isHcsfDurationCompliant,
            durationScenarios = scenarios
        )
    }

    fun formatEuro(amount: Long): String {
        val str = kotlin.math.abs(amount).toString()
        val sb = StringBuilder()
        var count = 0
        for (i in str.length - 1 downTo 0) {
            sb.append(str[i])
            count++
            if (count % 3 == 0 && i != 0) sb.append(' ')
        }
        val formatted = sb.reverse().toString()
        return if (amount < 0) "-$formatted €" else "$formatted €"
    }
}
