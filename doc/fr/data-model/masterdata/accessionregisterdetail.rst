Collection AccessionRegisterDetail
##################################

Utilisation de la collection AccessionRegisterDetail
====================================================

Cette collection a pour vocation de référencer l'ensemble des informations sur les opérations d'entrée réalisées pour un service producteur. A ce jour, il y a autant d'enregistrements que d'opérations d'entrées effectuées pour ce service producteur, mais des évolutions sont d'ores et déjà prévues. Cette collection reprend les élements du bordereau de transfert.

Exemple de la description dans le XML d'entrée
==============================================

Les seuls élements issus du message ArchiveTransfer utilisés ici sont ceux correspondants à la déclaration des identifiants du service producteur et du service versant. Ils sont placés dans le bloc <ManagementMetadata>

::

  <ManagementMetadata>
           <OriginatingAgencyIdentifier>FRAN_NP_051314</OriginatingAgencyIdentifier>
           <SubmissionAgencyIdentifier>FRAN_NP_005761</SubmissionAgencyIdentifier>
  </ManagementMetadata>

Exemple de JSON stocké en base comprenant l'exhaustivité des champs
===================================================================

::

  {
      "_id": "aedqaaaaakhpuaosabkcgak4ebd7deiaaaaq",
      "_tenant": 2,
      "OriginatingAgency": "FRAN_NP_009734",
      "SubmissionAgency": "FRAN_NP_009734",
      "ArchivalAgreement": "ArchivalAgreement0",
      "EndDate": "2017-05-19T12:36:52.572+02:00",
      "StartDate": "2017-05-19T12:36:52.572+02:00",
      "Symbolic": true,
      "Status": "STORED_AND_COMPLETED",
      "LastUpdate": "2017-05-19T12:36:52.572+02:00",
      "TotalObjectGroups": {
          "ingested": 0,
          "deleted": 0,
          "remained": 0
          "attached": 0,
          "detached": 0,
          "symbolicRemained": 0
      },
      "TotalUnits": {
          "ingested": 11,
          "deleted": 0,
          "remained": 11
          "attached": 0,
          "detached": 0,
          "symbolicRemained": 0
      },
      "TotalObjects": {
          "ingested": 0,
          "deleted": 0,
          "remained": 0
          "attached": 0,
          "detached": 0,
          "symbolicRemained": 0
      },
      "ObjectSize": {
          "ingested": 0,
          "deleted": 0,
          "remained": 0
          "attached": 0,
          "detached": 0,
          "symbolicRemained": 0
      },
      "OperationIds": [
          "aedqaaaaakhpuaosabkcgak4ebd7deiaaaaq"
      ],
    "_v": 5
  }

Détail des champs
=================

**"_id":** identifiant unique.

  * Il s'agit d'une chaîne de 36 caractères correspondant à un GUID.
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"_tenant": Champ obligatoire peuplé par Vitam** identifiant du tenant.

  * Il s'agit d'un entier.
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"OriginatingAgency":** contient l'identifiant du service producteur.
  Il est issu du le bloc <OriginatinAgencyIdentifier> correspondant au champ Name de la collection Agencies.

Par exemple :

::

  <OriginatingAgencyIdentifier>FRAN_NP_051314</OriginatingAgencyIdentifier>

on récupère la valeur FRAN_NP_051314

  * Il s'agit d'une chaîne de caractères.
  * Cardinalité : 0-1

**"SubmissionAgency":** contient l'identifiant du service versant.
    Il est contenu entre les balises <SubmissionAgencyIdentifier> correspondant au champ Name de la collection Agencies.

Par exemple pour

::

  <SubmissionAgencyIdentifier>FRAN_NP_005761</SubmissionAgencyIdentifier>

On récupère la valeur FRAN_NP_005761.

  * Il s'agit d'une chaîne de caractère.
  * Cardinalité : 1-1

Ce champ est facultatif dans le bordereau. S'il' est absente ou vide, alors la valeur contenue dans le champ <OriginatingAgencyIdentifier> est reportée dans ce champ.

**"ArchivalAgreement":** Contient le contrat utilisé pour réaliser l'entrée.
  Il est contenu entre les balises <ArchivalAgreement> et correspond à la valeur contenue dans le champ Identifier de la collection IngestContract.

Par exemple pour

::

  <ArchivalAgreement>IC-000001</ArchivalAgreement>

On récupère la valeur IC-000001.

  * Il s'agit d'une chaîne de caractère.
  * Cardinalité : 1-1

**"EndDate":** date de la dernière opération d'entrée pour l'enregistrement concerné. 

  * La date est au format ISO 8601

  ``"EndDate": "2017-04-10T11:30:33.798"``

  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"StartDate":** date de la première opération d'entrée pour l'enregistrement concerné. 

  * La date est au format ISO 8601

  ``"StartDate": "2017-04-10T11:30:33.798"``

  * Champ peuplé par Vitam.
  * Cardinalité : 1-1
 
**Symbolic**: Indique si le fonds concerné est propre au service producteur ou s'il lui est rattaché symboliquement. Si le champ correspond à la valeur true, il s'agit de liens symboliques.

  * Il s'agit d'un booléen
  * Cardinalité : 1-1

**"Status":**. Indication sur l'état des archives concernées par l'enregistrement.

  * Il s'agit d'une chaîne de caractères
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"LastUpdate":**. Date de la dernière mise à jour pour l'enregistrement concerné. 

  * La date est au format ISO 8601
  * Champ peuplé par Vitam

  ``"StartDate": "2017-04-10T11:30:33.798"``

  * Cardinalité : 1-1
 
**"TotalObjectGroups":**. Il contient la répartition du nombre de groupes d'objets du fonds par état pour l'opération journalisée (ingested, deleted,remained, attached, detached et symbolicRemained) :
    - "ingested": nombre de groupes d'objets pris en charge dans le cadre de l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "deleted": nombre de groupes d'objets supprimés ou sortis du système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "remained": nombre de groupes d'objets conservés dans le système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "attached": nombre de groupes d'objets rattachés symboliquement de ce service producteur pour l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "detached": nombre de groupes d'objets détachés symboliquement de ce service producteur. La valeur contenue dans ce champ est un entier.
    - "symbolicRemained": nombre actualisé de groupes d'objets attachés symboliquement de ce service producteur pour l'enregistrement concerné et conservés dans la solution logicielle Vitam. La valeur contenue dans ce champ est un entier.
      
  * Il s'agit d'un JSON
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"TotalUnits":**. Il contient la répartition du nombre d'unités archivistiques du fonds par état pour l'opération journalisée (ingested, deleted,remained, attached, detached et symbolicRemained) :
    - "ingested": nombre d'unités archivistiques prises en charge dans le cadre de l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "deleted": nombre d'unités archivistiques supprimées ou sorties du système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "remained": nombre d'unités archivistiques conservées dans le système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "attached": nombre d'unités archivistiques rattachées symboliquement de ce service producteur pour l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "detached": nombre d'unités archivistiques détachées symboliquement de ce service producteur. La valeur contenue dans ce champ est un entier.
    - "symbolicRemained": nombre actualisé d'unités archivistiques attachées symboliquement de ce service producteur pour l'enregistrement concerné et conservées dans la solution logicielle Vitam. La valeur contenue dans ce champ est un entier.
      
  * Il s'agit d'un JSON
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"TotalObjects":** Contient la répartition du nombre d'objets du fonds par état pour l'opération journalisée  (ingested, deleted,remained, attached, detached et symbolicRemained) :
    - "ingested": nombre  d'objets prises en charge dans le cadre de l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "deleted": nombre d'objets supprimés ou sorties du système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "remained": nombre d'objets conservées dans le système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "attached": nombre d'objets rattachées symboliquement de ce service producteur pour l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "detached": nombre d'objets détachées symboliquement de ce service producteur. La valeur contenue dans ce champ est un entier.
    - "symbolicRemained": Nombre actualisé d'objets attachées symboliquement de ce service producteur pour l'enregistrement concerné et conservés dans la solution logicielle Vitam. La valeur contenue dans ce champ est un entier.
      
  * Il s'agit d'un JSON
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"ObjectSize":** Contient la répartition du volume total des fichiers du fonds par état pour l'opération journalisée (ingested, deleted,remained, attached, detached et symbolicRemained) :
    - "ingested": volume en octet des fichiers pris en charge dans le cadre de l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "deleted": volume en octet des fichiers supprimés ou sortis du système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "remained": volume en octet des fichiers conservés dans le système pour l'enregistrement concerné. La valeur contenue dans ce champ est un entier.
    - "attached": volume en octet des fichiers rattachés symboliquement de ce service producteur pour l'enregistrement concerné. La valeur contenue dans le champ est un entier.
    - "detached": volume en octet des fichiers détachés symboliquement de ce service producteur. La valeur contenue dans ce champ est un entier.
    - "symbolicRemained": Volume actualisé en octets des fichiers attachés symboliquement de ce service producteur pour l'enregistrement concerné et conservés dans la solution logicielle Vitam. La valeur contenue dans ce champ est un entier.
    
  * Il s'agit d'un JSON
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"OperationIds":** opération d'entrée concernée

  * Il s'agit d'un tableau.
  * Ne peut être vide
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1

**"_v":** version de l'enregistrement décrit

  * Il s'agit d'un entier.
  * Champ peuplé par Vitam.
  * Cardinalité : 1-1