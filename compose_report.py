from pathlib import Path
import xml.etree.ElementTree as ET
import jinja2
import sys

tests = [
    ("RotateTestsLite", "Proste testy obsługi rotate", 0.5),
     ("RotateTestsFull", "Pełne testy obsługi rotate", 0.5),
      ("BothOpsTestsLite", "Proste testy obsługi rotate i show", 0.5),
       ("BothOpsTestsFull", "Pełne testy obsługi rotate i show", 0.5),
       ("InterruptionTestsLite", "Podstawowa obsługa przerwań", 1),
        ("InterruptionCorrectnessFull", "Pełna obsługa przerwań", 1),
         ("ParallelExec1", "Równoległe wykonywanie dozwolonych operacji (I)", 0.3),
          ("ParallelExec2",  "Równoległe wykonywanie dozwolonych operacji (II)", 0.3),
           ("ParallelExec3",  "Równoległe wykonywanie dozwolonych operacji (III)", 0.4),
           ("SeqExec1", "Sekwencyjne wykonywanie wykluczających się operacji (I)", 0.25),
           ("SeqExec2", "Sekwencyjne wykonywanie wykluczających się operacji (II)", 0.25),
           ("SeqExec3", "Sekwencyjne wykonywanie wykluczających się operacji (III)", 0.25),
           ("SeqExec4", "Sekwencyjne wykonywanie wykluczających się operacji (IV)", 0.25),
           ("LivelinessTests", "Żywotność rozwiązania", 1)
]

def main():
    sol_dir = Path(sys.argv[1])
    results = []

    for name, desc, pts in tests:
        xml_file = f"{name}/xml/TEST-concurrentcube.CubeTest${name}.xml"
        xml_file_path = sol_dir / Path(xml_file)
        if xml_file_path.exists():
            tree = ET.parse(xml_file_path)
            root = tree.getroot()
            failures = int(root.attrib["failures"])
            errors = int(root.attrib["errors"])
            skipped = int(root.attrib["skipped"])
            passed = (failures == 0 and errors == 0 and skipped == 0)
            results.append((desc, pts if passed else 0))
        else:
            results.append((desc, 0))

    total = sum(pts for desc, pts in results)

    t = jinja2.Template(open("report.txt.j2", "r").read())
    print(t.render({"results": results, "total": total}))


if __name__ == "__main__":
    main()
