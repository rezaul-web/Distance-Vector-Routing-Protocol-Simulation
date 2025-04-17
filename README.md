# Distance Vector Routing Simulation (Jetpack Compose Desktop) 📈

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
<!-- Add other badges if you have CI/CD setup, e.g., build status -->

A visual and interactive simulation of the **Distance Vector Routing (DVR)** protocol, built using **Kotlin** and **Jetpack Compose for Desktop**. This application serves as an educational tool to understand how DVR works by allowing users to visualize the network topology, observe the step-by-step convergence process, manage network links dynamically, and find the shortest paths based on the calculated routing tables.

---

## 📸 Screenshot / GIF

![DVR Simulation Screenshot](placeholder.png)
***(Replace this placeholder with an actual screenshot or GIF of your application!)** A visual demonstration is highly recommended.*

---

## ✨ Features

*   ✅ **Interactive Graph Visualization:** Displays network nodes and edges with associated costs using Jetpack Compose Canvas.
*   🖱️ **Node & Edge Selection:** Click on nodes or edges to highlight them and view specific details (like a node's routing table).
*   🔗 **Dynamic Link Management:**
    *   ➕ Add new bidirectional links (edges) between nodes with specified costs.
    *   ➖ Remove existing links.
    *   ✏️ Update the cost (weight) of existing links.
*   ⏯️ **Step-by-Step Simulation:** Manually advance the DVR algorithm one step at a time to observe how routing tables are exchanged and updated (Bellman-Ford logic).
*   🚀 **Auto-Step Simulation:** Automatically run simulation steps with visual feedback until convergence is reached. Includes pause functionality.
* **Visual DVR Exchange:** See symbolic "packets" representing distance vector advertisements travel along the links during simulation steps.
*   📊 **Routing Table Display:** View the current routing table (`Destination`, `Cost`, `Next Hop`) for any selected node, updating in real-time as the simulation progresses.
 **Shortest Path Finding:** Calculate and visualize the shortest path between any two nodes based on the *current state* of the converged (or partially converged) routing tables. Path is highlighted on the graph.
*   🏁 **Convergence Detection:** The simulation clearly indicates when the DVR algorithm has stabilized and all routing tables are consistent.
*   🎨 **Clear Visual Feedback:** Utilizes distinct colors and highlights for selections, active advertisements, calculated paths, and simulation status messages.
*   🔄 **Reset Functionality:** Easily reset the entire simulation back to its initial network topology and state.

---

## 🛠️ Technology Stack

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose for Desktop
*   **Asynchronous Operations:** Kotlin Coroutines (for smooth animations and non-blocking simulation steps)
*   **Build System:** Gradle

---

## 📋 Prerequisites

*   **JDK:** Java Development Kit version 11 or higher. (Ensure `JAVA_HOME` is set correctly).
*   **Gradle:** Usually bundled with IntelliJ IDEA or installable separately. The project includes a Gradle wrapper (`gradlew`).
*   **IDE (Recommended):** IntelliJ IDEA (Community or Ultimate) for the best Kotlin and Compose development experience.

---

## 🚀 Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/rezaul-web/Distance-Vector-Routing-Protocol-Simulation.git
    cd Distance-Vector-Routing-Protocol-Simulation
    ```

2.  **Open in IntelliJ IDEA:**
    *   Launch IntelliJ IDEA.
    *   Select `File` > `Open...` and navigate to the cloned `Distance-Vector-Routing-Protocol-Simulation` directory.
    *   Trust the project if prompted.
    *   Allow IntelliJ to import the Gradle project and download dependencies (this might take a few minutes).

3.  **Build the Project:**
    *   Use the IDE: `Build` > `Build Project` (or press `Ctrl+F9` / `Cmd+F9`).
    *   *Alternatively, via terminal:*
        ```bash
        ./gradlew build     # On Linux/macOS
        gradlew.bat build   # On Windows
        ```

---

## ▶️ Running the Application

1.  **From IntelliJ IDEA (Recommended):**
    *   Find the `Main.kt` file (likely in `src/main/kotlin/`).
    *   Locate the `main` function within the file.
    *   Click the green ▶️ icon in the gutter next to the `main` function.
    *   Select "Run 'MainKt'".

2.  **From the Command Line (using Gradle):**
    ```bash
    ./gradlew run     # On Linux/macOS
    gradlew.bat run   # On Windows
    ```

---

## 📖 How to Use the Simulation

The application window is divided into the main visualization canvas on the left and control panels/table display on the right.

1.  🖱️ **Canvas Interaction:**
    *   **View:** Observe the network graph (nodes and links with costs).
    *   **Select Node:** Click directly on a node circle. It will be highlighted (e.g., Magenta), and its routing table will appear in the bottom-right panel.
    *   **Select Edge:** Click near the line representing a link. It will be highlighted (e.g., Orange).
    *   **Deselect:** Click on any empty space in the canvas area.

2.  🔗 **Link Management (Bottom Left):**
    *   Input `Node 1 ID`, `Node 2 ID`, and `Cost`.
    *   **Add:** Creates a link if valid and non-existent.
    *   **Remove:** Deletes the specified link.
    *   **Update Cost:** Modifies the cost of an existing link.
    *   *Note:* Changes reset the convergence state and require re-running the simulation.

3.  ⚙️ **Simulation Controls (Top Right):**
    *   **Step:** Executes one full round of DVR updates across all nodes. Visualize the DV exchange animations. Disabled when converged or during auto-step.
    *   **Auto Step / Pause:** Starts/stops automatic execution of steps with delays until convergence.
    *   **Reset Sim:** Returns the network to its initial configuration and clears all calculated data.

    *   Enter `Source ID` and `Dest ID`.
    *   **Show Path:** Uses the *current* routing tables to trace and highlight the calculated shortest path on the canvas (Nodes: Green/Orange, Edges: Red). Visual feedback indicates if a path exists based on the current table state.

5.  📊 **Routing Table Display (Bottom Right):**
    *   Shows the table for the currently selected node (`Destination`, `Cost`, `Next Hop`).
    *   Updates dynamically after each simulation step if changes occur for the selected node. `INF` indicates an unreachable destination.

6.  💬 **Status Messages:**
    *   Look below the "Find Path" section for feedback on actions (link added/removed, step completed, convergence reached, path found, errors, etc.).

---

## 💡 How It Works (Conceptual)

1.  **Initialization:** Starts with a base network topology. Each node initializes its table: 0 cost to itself, direct link cost to neighbors, and infinity (`INF`) to others.
2.  **Bellman-Ford Logic (Core of DVR):** In each `Step`, nodes exchange their distance vectors with neighbors. When node `A` receives a vector from neighbor `B`:
    *   For every destination `D` in `B`'s vector, `A` calculates `Cost(A, D) = Cost(A, B) + Cost(B, D)`.
    *   If this calculated cost is *less* than `A`'s current known cost to `D`, `A` updates its table: sets the new lower cost and sets `Next Hop` for `D` to `B`.
3.  **Visualization:** The simulation animates this exchange and highlights table changes.
4.  **Convergence:** The process repeats until no node updates its table during a full step, meaning the shortest paths (according to DVR) have been found.
5.  **Path Tracing:** The "Show Path" function iteratively looks up the `Next Hop` in each node's table, starting from the source, until the destination is reached.

*(Note: This simulation demonstrates the core DVR principle. It may not implement advanced optimizations like Split Horizon or Poison Reverse unless explicitly coded.)*

---

## 🙌 Contributing

Contributions are welcome! If you find bugs, have suggestions for improvements, or want to add new features:

1.  **Open an Issue:** Describe the bug or feature suggestion clearly in the repository's "Issues" tab.
2.  **Fork & Pull Request:** Fork the repository, make your changes on a separate branch, and submit a pull request detailing your changes.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for full details.
