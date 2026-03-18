Gem::Specification.new do |s|
  s.name        = "chrondb"
  s.version     = "0.1.0"
  s.summary     = "Ruby client for ChronDB"
  s.description = "Ruby bindings for ChronDB — a time-traveling key/value database built on Git architecture. Auto-generated from the Rust SDK via UniFFI."
  s.authors     = ["Thiago Avelino"]
  s.email       = "avelinorun@gmail.com"
  s.homepage    = "https://github.com/avelino/chrondb"
  s.license     = "AGPL-3.0"

  s.files       = Dir["lib/**/*.rb", "lib/**/*.so", "lib/**/*.dylib"]
  s.require_paths = ["lib"]

  s.required_ruby_version = ">= 3.0"
end
