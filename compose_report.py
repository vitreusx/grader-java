from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    test_results_path = Path("build/test-results/test")
    lines = []
    points = 0
    for test_suite in test_results_path.iterdir():
        if test_suite.is_file() and test_suite.suffix == ".xml":
            tree = ET.parse(test_suite)
            root = tree.getroot()
            failures = int(root.attrib["failures"])
            errors = int(root.attrib["errors"])
            name = root.attrib["name"]
            passed = (failures == 0 and errors == 0)
            if passed:
                points += 1
            lines.append(f"{'PASSED' if passed else 'FAILED'} | {name}")

    print(f'Points: {points}/7')
    for line in lines:
        print(line)


if __name__ == "__main__":
    main()
