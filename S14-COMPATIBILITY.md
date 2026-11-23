# Fewer S14 compatibility branch

This branch starts from the S13 1.7.1 source, preserving its suffix database schema, LuckPerms suffix groups, permission names and commands. Version 1.7.2 targets Paper 26.3 and replaces removed Adventure click actions and the old DURABILITY enchantment name.

To keep database credentials out of a deployment repository, configuration may specify `database.secrets-file: database-credentials.yml`. That local file contains the existing `url`, `username`, and `password` keys. Keep it inside the SuffixesPlus plugin folder, exclude it from Git, and preserve it across normal startups. Inline S13 database configuration remains supported. Missing/invalid private configuration fails closed.

No database reset, entitlement migration or LuckPerms group rewrite is performed. Activation is deferred to the normal server restart; live functionality must be checked afterward.
