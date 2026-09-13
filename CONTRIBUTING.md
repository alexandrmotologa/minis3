# Contributing to MiniS3

Thank you for your interest in contributing to MiniS3! We welcome contributions of all kinds, whether you are fixing a bug, improving documentation, or proposing new features.

---

## Code of Conduct

Please treat everyone in the community with respect, kindness, and constructive feedback. Open source thrives when developers collaborate positively.

---

## How to Contribute

### 1. Reporting Bugs & Requesting Features
- **Search existing issues** first to avoid duplicates.
- **For bugs:** Open an issue describing the bug, including steps to reproduce, expected vs. actual behavior, and environment details (Java 21 LTS and build details (OS, JDK distribution, Maven version)).
- **For feature requests:** Describe the problem you are trying to solve and propose a solution or interface specification.

### 2. Pull Request Workflow

1. **Fork the repository** and clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/minis3.git
   cd minis3
   ```

2. **Create a topic branch** from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   # or: git checkout -b fix/issue-description
   ```

3. **Follow commit conventions:** We follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: add bounded queue backpressure strategy`
   - `fix: correct memory alignment in off-heap buffer`
   - `docs: improve benchmark notes in README`
   - `perf: reduce allocation footprint in ring buffer`

4. **Ensure code quality:**
   - Keep code clean, readable, and strictly typed.
   - Verify that all existing and new unit tests pass before submitting.
   - Run the local linter/formatter if available.

5. **Push and open a Pull Request:**
   - Push your branch to your fork:
     ```bash
     git push origin feat/your-feature-name
     ```
   - Open a Pull Request against the `main` branch.
   - Provide a clear PR title and description outlining the changes made and referencing any related issues (e.g., `Closes #12`).

---

## Development Setup

MiniS3 is built on **Java 21 LTS** using Apache Maven.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/alexandrmotologa/minis3.git
   cd minis3
   ```

2. **Build and run test suite:**
   ```bash
   mvn clean verify
   ```

3. **Code standards:**
   - Adhere to modern Java 21 idioms (records, pattern matching, virtual threads where applicable).
   - Keep classes cohesive, immutable by default, and provide comprehensive unit tests under `src/test/java`.

Refer to the **Quick Start** section in [README.md](README.md) for full configuration flags, architecture details, and usage examples.

---

## Questions & Discussions

If you have questions about architecture decisions or need guidance before submitting a large change, feel free to open a Discussion or an Issue with the `question` label.
