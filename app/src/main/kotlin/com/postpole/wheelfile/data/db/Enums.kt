package com.postpole.wheelfile.data.db

enum class VehicleType { CAR, MOTORBIKE, VAN, OTHER }

enum class Powertrain { PETROL, DIESEL, LPG, CNG, ELECTRIC, PHEV, HYBRID, HYDROGEN }

enum class DistanceUnit { KM, MI }

enum class VolumeUnit { L, US_GAL, UK_GAL, KG }

enum class EconomyUnit { L_PER_100KM, KM_PER_L, MPG_US, MPG_UK, KWH_PER_100KM, MI_PER_KWH, KWH_PER_100MI, KG_PER_100KM }

/** Which energy stream an [EnergyLog] belongs to. A PHEV has both LIQUID and ELECTRIC streams. */
enum class EnergyStream { LIQUID, ELECTRIC, GAS_KG }

enum class ChargerType { HOME, AC_PUBLIC, DC_FAST }

enum class ExpenseCategory { INSURANCE, TAX_REGISTRATION, PARKING, TOLLS, WASHING, FINES, LOAN_LEASE, ACCESSORIES, OTHER }

enum class Recurrence { MONTHLY, YEARLY }

enum class ReminderKind { DATE, ODOMETER, FIRST_OF }

enum class DocumentType { INSURANCE, REGISTRATION, INSPECTION, LICENCE, WARRANTY, ROADSIDE_COVER, LEASE, CUSTOM }

enum class OwnerType { ENERGY, SERVICE, EXPENSE, DOCUMENT, VEHICLE }
