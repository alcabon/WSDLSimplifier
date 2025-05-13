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

Compile
javac WSDLSimplifier.java

Run
java WSDLSimplifier

text
The simplified WSDL will be written to `SimplifiedAccountSOAPService.wsdl`.

## Project Structure

```
.
├── WSDLSimplifier.java # Main utility with comments
└── AccountSOAPService.xml # Input WSDL (user-provided)
```

text

## Contributing
1. Fork the repository  
2. Create a feature branch (`git checkout -b my-feature`)  
3. Commit your changes (`git commit -m "Add my feature"`)  
4. Push to your branch (`git push origin my-feature`)  
5. Open a Pull Request

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
