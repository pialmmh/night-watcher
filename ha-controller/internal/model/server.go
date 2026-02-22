package model

// Server represents a physical or virtual host machine.
type Server struct {
	ID       string     `yaml:"id"        json:"id"`
	Address  string     `yaml:"address"   json:"address"`
	SSH      *SSHConfig `yaml:"ssh"       json:"ssh,omitempty"`
	FenceCmd string     `yaml:"fence_cmd" json:"fenceCmd,omitempty"`
}

// SSHConfig holds SSH connection details.
type SSHConfig struct {
	User    string `yaml:"user"     json:"user"`
	KeyPath string `yaml:"key_path" json:"keyPath"`
	Port    int    `yaml:"port"     json:"port"`
}
