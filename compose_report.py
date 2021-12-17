from pathlib import Path
import xml.etree.ElementTree as ET
import jinja2
import sys

xml_files = [
    f"{name}/xml/TEST-concurrentcube.CubeTest${name}.xml"
    for name in [
        "RotateTests", "CorrectnessOfBoth", "InterruptionCorrectnessTests",
        "InterruptingWaitingTests", "ParallelExecutionTests",
        "SequentialityTests", "LivelinessTests"
    ]
]


def main():
    sol_dir = Path(sys.argv[1])
    points = [0 for _ in range(len(xml_files))]
    for idx, xml_file in enumerate(xml_files):
        xml_file_path = sol_dir / Path(xml_file)
        if xml_file_path.exists():
            tree = ET.parse(xml_file_path)
            root = tree.getroot()
            failures = int(root.attrib["failures"])
            errors = int(root.attrib["errors"])
            skipped = int(root.attrib["skipped"])
            passed = (failures == 0 and errors == 0 and skipped == 0)
            points[idx] = 1 if passed else 0

    total = sum(points)

    t = jinja2.Template(open("report.md.j2", "r").read())
    print(t.render({"points": points, "total": total}))


if __name__ == "__main__":
    main()
