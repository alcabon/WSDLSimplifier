# WSDLSimplifier

A command-line Java utility to simplify WSDL files by removing unused SOAP/WSDL elements and pruning unreferenced XML Schema types.

## Features
- Strips out unused `<message>`, `<portType>`, `<binding>`, and `<service>` blocks.
- Builds a dependency graph of XSD types to retain only necessary types.
- Removes extraneous blank lines for clean, readable output.

## Prerequisites
- Java 8 or higher
- Maven or your preferred build tool (optional)
- Input WSDL file named `AccountSOAPService.xml` in the working directory

## Usage
