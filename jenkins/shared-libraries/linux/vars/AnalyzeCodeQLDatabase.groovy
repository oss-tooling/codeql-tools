def call(repo, language) {
    env.DATABASE_BUNDLE = sprintf("%s-database.zip", language)
    env.DATABASE_PATH = sprintf("%s-%s", repo, language)
    if(!env.ENABLE_DEBUG) {
        env.ENABLE_DEBUG = false
    }
    if(!env.ENABLE_CODEQL_DEBUG) {
        env.ENABLE_CODEQL_DEBUG = false
    }
    env.LANGUAGE = language.toLowerCase()
    if(["javascript" , "python", "ruby"].contains(env.LANGUAGE)) {
        env.COMPILED_LANGUAGE = false
    } else {
        env.COMPILED_LANGUAGE = true
    }
    env.SARIF_FILE = sprintf("%s-%s.sarif", repo, language)
    env.QL_PACKS = sprintf("codeql/%s-queries:codeql-suites/%s-security-and-quality.qls", language, language)

    sh """
        set +x
        if [ "${ENABLE_DEBUG}" = true ]; then
            set -x
        fi

        command="codeql"
        if [ ! -x "\$(command -v \$command)" ]; then
            echo "CodeQL CLI not found on PATH, checking if local copy exists"
            if [ ! -f "${WORKSPACE}/codeql/codeql" ]; then
                echo "CodeQL CLI not found in local copy, please add the CodeQL CLI to your PATH or use the 'InstallCodeQL' command to download it"
                exit 1
            fi
            echo "Using local copy of CodeQL CLI"
            command="${WORKSPACE}/codeql/codeql"
        fi

        if [ "${BUILD_COMMAND}" = "" ] && [ "${COMPILED_LANGUAGE}" = true ]; then
            echo "Finalizing database"
            "\$command" database finalize "${DATABASE_PATH}"
            echo "Database finalized"
        fi

        category=\$( echo "${LANGUAGE}" | tr '[:upper:]' '[:lower:]' )
        echo "Checking if current working directory and Jenkins workspace are the same directory"
        if [ "\${PWD}" != "${WORKSPACE}" ]; then
            echo "The current working directory and Jenkins workspace do not match, updating the SARIF category value to deduplicate Code Scanning results"
            category=$(basename $PWD | sed 's/ /-/g')
        fi
        echo "The SARIF category has been configured to \$category"

        echo "Analyzing database"
        "\$command" database analyze "${DATABASE_PATH}" --no-download --threads 0 --sarif-category "\$category" --format sarif-latest --output "${SARIF_FILE}" "${QL_PACKS}"
        echo "Database analyzed"

        if [ "${ENABLE_CODEQL_DEBUG}" = true ]; then
            echo "Checking for failed extractions"
            "\$command" bqrs decode "${DATABASE_PATH}/results/codeql/${LANGUAGE}-queries/Diagnostics/ExtractionErrors.bqrs"
        fi

        echo "Generating CSV of results"
        "\$command" database interpret-results "${DATABASE_PATH}" --format=csv --output="codeql-scan-results-${LANGUAGE}.csv" "${QL_PACKS}"
        echo "CSV of results generated"

        echo "Generating Database Bundle"
        "\$command" database bundle "${DATABASE_PATH}" --output "${DATABASE_BUNDLE}"
        echo "Database Bundle generated"
    """
}
