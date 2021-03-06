Workflow d'administration d'un référentiel des vocabulaires de l'ontologie
###########################################################################

Introduction
============

Cette section décrit le processus permettant d'importer des vocabulaires de l'ontologie. Cette opération n'est réalisable que sur le tenant d'administration.

Processus d'import et mise à jour des vocabulaires de l'ontologie (vision métier)
=================================================================================

Le processus d'import d'une ontologie permet d'ajouter des vocabulaires qui seront utilisés dans les profils d'unité archivistique (documents types)
Tous les éléments réalisés au cours de ce processus sont exécutés dans une seule étape.

Import des métadonnées d'une ontologie STP_IMPORT_ONTOLOGY (OntologyServiceImpl.java)
-------------------------------------------------------------------------------------

Vérification de la présence des informations minimales, de la cohérence des informations et affectation des données aux champs peuplés par la solution logicielle Vitam.


+ **Règle** : l'ontologie répond aux exigences suivantes :
 
    + Le fichier est au format Json.

    + Les données suivantes sont obligatoires :

	      * Le champ "Identifier" est peuplé d'une chaîne de caractères
	      * Le champ "Type" est peuplé par une valeur comprise dans la liste :

				- TEXT
				- KEYWORD
				- DATE
				- LONG
				- DOUBLE
				- BOOLEAN
				- GEO_POINT
				- ENUM

	      * Le champ "Origin" est peuplé par la valeur "EXTERNAL" ou "INTERNAL". L'INTERNAL correspond à l'ontologie interne de VITAM embarquée dans la solution
	   
     + Les données suivantes sont facultatives; si elles sont remplies, elles respectent les règles énoncées pour chacune : 

	      * Le champ "SedaField" est peuplé d'une chaîne de caractères
	      * Le champ "ApiField" est peuplé d'une chaîne de caractères
	      * Le champ "Description" est peuplé d'une chaîne de caractères
	      * Le champ "ShortName" correspond au champ traduction, il est peuplé par une chaîne de valeur
	      * Le champ "Collections" indique la collection dans laquelle le vocabulaire est rattaché, ex : "Unit"

	Exemple ontologie :

	.. code-block:: json

		[ {
		"Identifier" : "AcquiredDate",
		"SedaField" : "AcquiredDate",
		"ApiField" : "AcquiredDate",
		"Description" : "unit-es-mapping.json",
		"Type" : "DATE",
		"Origin" : "INTERNAL",
		"ShortName" : "AcquiredDate",
		"Collections" : [ "Unit" ]
		}, {
		"Identifier" : "BirthDate",
		"SedaField" : "BirthDate",
		"ApiField" : "BirthDate",
		"Description" : "unit-es-mapping.json",
		"Type" : "DATE",
		"Origin" : "INTERNAL",
		"ShortName" : "BirthDate",
		"Collections" : [ "Unit" ]
		}]



  + **Statuts** :

    - OK : les règles ci-dessus sont respectées (IMPORT_ONTOLOGY.OK = Succès du processus d'import de l'ontologie)

    - KO : une des règles ci-dessus n'a pas été respectée (IMPORT_ONTOLOGY.KO = Echec du processus d'import de l'ontologie)

    - FATAL : une erreur technique est survenue lors de la vérification de l'import de l'ontologie (IMPORT_ONTOLOGY.FATAL = Erreur technique lors du processus d'import de l'ontologie)

    - WARNING : Avertissement lors du processus d'import de l'ontologie ( IMPORT_ONTOLOGY.WARNING = Avertissement lors du processus d'import de l'ontologie )

 
Mise à jour d'une ontologie
---------------------------

La modification d'une ontologie s'effectue par ré-import du fichier Json. Le nouvel import annule et remplace l'ontologie précédente. Ce ré-import observe les règles décrites dans le processus d'import, décrit plus haut.  

Note : la mise à jour des vocabulaires de l'ontologie doit respecter certaines règles de compatibilité concernant la valeur du "Type":

		- Le champ Type TEXT peut être modifié en KEYWORD, TEXT
		- Le champ Type KEYWORD peut être modifié en KEYWORD, TEXT
		- Le champ Type DATE peut être modifié en KEYWORD, TEXT
		- Le champ Type LONG peut être modifié en KEYWORD, TEXT, DOUBLE
		- Le champ Type DOUBLE peut être modifié en KEYWORD, TEXT
		- Le champ Type BOOLEAN peut être modifié en KEYWORD, TEXT
		- Le champ Type GEO-POINT peut être modifié en KEYWORD, TEXT
		- Le champ Type ENUM de valeur peut être modifié en KEYWORD, TEXT


 + **Statuts** :

    - OK : les règles ci-dessus sont respectées (IMPORT_ONTOLOGY.OK = Succés du processus d'import de l'ontologie)

    - KO : une des règles ci-dessus n'a pas été respectée (IMPORT_ONTOLOGY.KO = Echec du processus d'import de l'ontologie)

    - FATAL : une erreur technique est survenue lors de la vérification de l'import de l'ontologie (IMPORT_ONTOLOGY.FATAL = Erreur technique lors du processus d'import de l'ontologie)

    - WARNING : Avertissement lors du processus d'import de l'ontologie (IMPORT_ONTOLOGY.WARNING = Avertissement lors du processus d'import de l'ontologie)



Sauvegarde du JSON (STP_BACKUP_ONTOLOGY)
-----------------------------------------------

Cette tâche est appellée en import initial de l'ontologie et lors de la modification de l'ontologie. 

  + **Règle** : enregistrement d'une copie de la base de données des métadonnées sur le stockage

  + **Type** : bloquant

  + **Statuts** :

      - OK : une copie de la base de données nouvellement importée est enregistrée (BACKUP_ONTOLOGY.OK = Succès du processus de sauvegarde des ontologies)

      - KO : Echec du processus de sauvegarde de l'ontologie (BACKUP_ONTOLOGY.KO=Echec du processus de sauvegarde des ontologies)
	
      - FATAL : une erreur technique est survenue lors de la sauvegarde des ontologies (BACKUP_ONTOLOGY.FATAL=Erreur technique lors du processus de sauvegarde des ontologies)





