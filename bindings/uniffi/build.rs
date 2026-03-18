fn main() {
    uniffi::generate_scaffolding("src/chrondb.udl").unwrap();
}
