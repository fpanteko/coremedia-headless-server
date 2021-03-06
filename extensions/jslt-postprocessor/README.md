# JSLT Postprocessor Example

This module demonstrates the use of postprocessors to transform the GraphQL query response into
JSON not easily generated by GraphQL queries or even violating GraphQL constraints. Use cases i.e. are
legacy systems which are consuming custom JSON formats.

The JSLT postprocessor uses the [JSLT library](https://github.com/schibsted/jslt). Check the GitHub repository
for further documentation on the available transformation rules.

## Configuration

1. Enable the extension by commenting out the dependency in `/extensions/extensions-bom/pom.xml`.
2. Mark the queries which you want to enable for postprocessing by adding the `jslt` option in the query header, i.e.
    ```
    #!query name=sites view=navigation type=CMSiteImpl jslt=meta
    ```
3. Add a transformation template named after the given option value to the `/src/main/resources/jslt` directory of
   this module.
4. Rebuild the server.
