Collection PersonalCertificate
##############################

Utilisation de la collection PersonalCertificate
================================================

La collection Certificate permet de référencer et décrire unitairement les certificats pour l'authentification personae.

Exemple de JSON stocké en base comprenant l'exhaustivité des champs
===================================================================

::

 {
    "_id": "aeaaaaaaaagcksdyabraialabxzw7aqaaaaq",
    "SubjectDN": "O=VITAM, L=Paris, C=FR",
    "SerialNumber": 2,
    "IssuerDN": "O=VITAM, L=Paris, C=FR",
    "Certificate": "MIIFRjCCAy6gAwIBAgIBAjANBgkqhkiG9w0BAQsFADAtMQswCQYDV...iaA==",
    "Hash": "6088f19bc7d328f301168c064d6fda93a6c4ced9d5c56810c4f70e21e77d841d"
 }

Détail des champs du JSON stocké dans la collection
===================================================

**"_id":** identifiant unique du certificat

  * Champ peuplé par Vitam
  * Il s'agit d'une chaîne de 36 caractères correspondant à un GUID.
  * Cardinalité : 1-1

**"SubjectDN":** Identifiant unique (Distinguished Name) du certificat

  * Il s'agit d'une chaîne de caractères
  * Cadinalité : 1-1

**"SerialNumber":** Numéro de série du certificat

  * Il s'agit d'un entier
  * Cadinalité : 1-1

**"IssuerDN":** Identifiant unique (Distinguished Name) de l'autorité de certification

  * Il s'agit d'une chaîne de caractères
  * Cadinalité : 1-1

**"Certificate":** Certificat au format DER encodé en Base64.

  * Il s'agit d'une chaîne de caractères
  * Cadinalité : 1-1

**"Hash":** Hash (SHA256) du certificat

  * Il s'agit d'une chaîne de caractères
  * Cadinalité : 1-1