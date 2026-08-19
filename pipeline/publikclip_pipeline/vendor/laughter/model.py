# Vendored from jrgillick/laughter-detection (MIT) — models.py, inference
# subset only (ResidualBlock + ResNetBigger, training helpers stripped).
# See VENDORED-LICENSES.md. Upstream: https://github.com/jrgillick/laughter-detection
#
# Faithful to the checkpoint at checkpoints/in_use/resnet_with_augmentation/
# best.pth.tar: conv biases ON (upstream deviates from the usual ResNet
# convention), AvgPool2d(4) before flatten (so a 44x128 window ends as
# 32ch x 1 x 4 = 128 features, matching bn2), linear_layer_size=128,
# filter_sizes=[128, 64, 32, 32]. Loaded strict=True — a mismatch here
# means running random weights and calling the output "laughter".

from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F


class ResidualBlock(nn.Module):
    def __init__(self, in_channels: int, out_channels: int, stride: int = 1):
        super().__init__()
        self.conv1 = nn.Conv2d(
            in_channels, out_channels, kernel_size=(3, 3), stride=stride, padding=1, bias=True
        )
        self.bn1 = nn.BatchNorm2d(out_channels)
        self.conv2 = nn.Conv2d(
            out_channels, out_channels, kernel_size=(3, 3), stride=1, padding=1, bias=True
        )
        self.bn2 = nn.BatchNorm2d(out_channels)
        self.shortcut = nn.Sequential()
        if stride != 1 or in_channels != out_channels:
            self.shortcut = nn.Sequential(
                nn.Conv2d(in_channels, out_channels, kernel_size=(1, 1), stride=stride, bias=False),
                nn.BatchNorm2d(out_channels),
            )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = nn.ReLU()(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        out = out + self.shortcut(x)
        return nn.ReLU()(out)


class ResNetBigger(nn.Module):
    def __init__(
        self,
        num_classes: int = 1,
        dropout_rate: float = 0.0,
        linear_layer_size: int = 128,
        filter_sizes: list[int] | None = None,
    ):
        super().__init__()
        filter_sizes = filter_sizes or [128, 64, 32, 32]
        self.conv1 = nn.Conv2d(
            in_channels=1, out_channels=64, kernel_size=(3, 3), stride=1, padding=1, bias=False
        )
        self.bn1 = nn.BatchNorm2d(64)
        self.linear_layer_size = linear_layer_size
        self.filter_sizes = filter_sizes
        self.block1 = self._create_block(64, filter_sizes[0], stride=1)
        self.block2 = self._create_block(filter_sizes[0], filter_sizes[1], stride=2)
        self.block3 = self._create_block(filter_sizes[1], filter_sizes[2], stride=2)
        self.block4 = self._create_block(filter_sizes[2], filter_sizes[3], stride=2)
        self.bn2 = nn.BatchNorm1d(linear_layer_size)
        self.bn3 = nn.BatchNorm1d(32)
        self.linear1 = nn.Linear(linear_layer_size, 32)
        self.linear2 = nn.Linear(32, num_classes)
        self.dropout = nn.Dropout(dropout_rate)

    @staticmethod
    def _create_block(in_channels: int, out_channels: int, stride: int) -> nn.Sequential:
        return nn.Sequential(
            ResidualBlock(in_channels, out_channels, stride),
            ResidualBlock(out_channels, out_channels, 1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = nn.ReLU()(self.bn1(self.conv1(x)))
        out = self.block1(out)
        out = self.block2(out)
        out = self.block3(out)
        out = self.block4(out)
        out = nn.AvgPool2d(4)(out)
        out = out.view(out.size(0), -1)
        out = self.bn2(out)
        out = self.dropout(out)
        out = self.linear1(out)
        out = self.bn3(out)
        out = self.dropout(out)
        out = F.relu(out)
        out = self.linear2(out)
        return torch.sigmoid(out)


def load_model(checkpoint_path: str, device: torch.device) -> ResNetBigger:
    model = ResNetBigger(dropout_rate=0.0, linear_layer_size=128, filter_sizes=[128, 64, 32, 32])
    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)
    state = checkpoint.get("state_dict", checkpoint)
    model.load_state_dict(state, strict=True)
    model.to(device)
    model.eval()
    return model
