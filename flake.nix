{
  description = "SOPSie JetBrains plugin dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk21
            gradle_9
            just
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}"
            echo "SOPSie JetBrains plugin dev shell"
            echo "  java $(java --version 2>&1 | head -1)"
            echo "  gradle $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'available')"
            echo "  sops $(sops --version 2>&1 | head -1)"
            echo ""
            echo "Run 'just' to see available commands."
          '';
        };
      }
    );
}
