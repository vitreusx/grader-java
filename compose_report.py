from pathlib import Path
import xml.etree.ElementTree as ET
import jinja2
import sys

order = {
    "Tests for concurrent rotate operations.": 0,
    "Tests for correctness of rotate and show at the same time.": 1,
    "Tests for the correctness of the operations in the presence of interruptions.": 2,
    "Tests for the behaviour of interruptions and waiting at locks.": 3,
    "Tests of the parallel execution of non-conflicting operations.": 4,
    "Tests of the sequentiality of conflicting operations.": 5,
    "Tests for the liveliness of the implementation.": 6
}


def main():
    test_results_path = Path(f"build/test-results/test")
    points = {}
    for test_suite in test_results_path.iterdir():
        if test_suite.is_file() and test_suite.suffix == ".xml":
            tree = ET.parse(test_suite)
            root = tree.getroot()
            failures = int(root.attrib["failures"])
            errors = int(root.attrib["errors"])
            name = root.attrib["name"]
            passed = (failures == 0 and errors == 0)
            points[name] = passed

    pts = [0 for _ in range(7)]
    for name in points:
        pts[order[name]] = 1 if points[name] else 0

    total = sum(points.values())

    t = jinja2.Template(open("report.md.j2", "r").read())
    print(t.render({"name": sys.argv[1], "pts": pts, "points": total}))


if __name__ == "__main__":
    main()
