# Vendored from qiuqiangkong/audioset_tagging_cnn (MIT) — pytorch/models.py
# inference subset: init helpers, ConvBlock, framewise interpolation, and
# Cnn14_DecisionLevelMax only. See VENDORED-LICENSES.md.
# Upstream: https://github.com/qiuqiangkong/audioset_tagging_cnn
#
# Temporal resolution note (measured from this source, closes a research
# gap): mel hop is 320 samples at 32 kHz = 10 ms frames, and
# interpolate_ratio = 32, so the model's real event resolution is 320 ms,
# linearly interpolated back onto the 10 ms frame grid.

from __future__ import annotations

import torch
import torch.nn as nn
import torch.nn.functional as F
from torchlibrosa.stft import LogmelFilterBank, Spectrogram

SAMPLE_RATE = 32000
WINDOW_SIZE = 1024
HOP_SIZE = 320
MEL_BINS = 64
FMIN = 50
FMAX = 14000
CLASSES_NUM = 527
FRAMES_PER_SEC = SAMPLE_RATE / HOP_SIZE  # 100 fps output grid


def init_layer(layer: nn.Module) -> None:
    nn.init.xavier_uniform_(layer.weight)
    if hasattr(layer, "bias") and layer.bias is not None:
        layer.bias.data.fill_(0.0)


def init_bn(bn: nn.Module) -> None:
    bn.bias.data.fill_(0.0)
    bn.weight.data.fill_(1.0)


def interpolate(x: torch.Tensor, ratio: int) -> torch.Tensor:
    """Upsample segmentwise posteriors back to the mel frame grid."""
    (batch_size, time_steps, classes_num) = x.shape
    upsampled = x[:, :, None, :].repeat(1, 1, ratio, 1)
    return upsampled.reshape(batch_size, time_steps * ratio, classes_num)


def pad_framewise_output(framewise_output: torch.Tensor, frames_num: int) -> torch.Tensor:
    pad = framewise_output[:, -1:, :].repeat(
        1, frames_num - framewise_output.shape[1], 1
    )
    return torch.cat((framewise_output, pad), dim=1)


class ConvBlock(nn.Module):
    def __init__(self, in_channels: int, out_channels: int):
        super().__init__()
        self.conv1 = nn.Conv2d(
            in_channels, out_channels, kernel_size=(3, 3), stride=(1, 1), padding=(1, 1), bias=False
        )
        self.conv2 = nn.Conv2d(
            out_channels, out_channels, kernel_size=(3, 3), stride=(1, 1), padding=(1, 1), bias=False
        )
        self.bn1 = nn.BatchNorm2d(out_channels)
        self.bn2 = nn.BatchNorm2d(out_channels)
        self.init_weight()

    def init_weight(self) -> None:
        init_layer(self.conv1)
        init_layer(self.conv2)
        init_bn(self.bn1)
        init_bn(self.bn2)

    def forward(self, x: torch.Tensor, pool_size=(2, 2), pool_type: str = "avg") -> torch.Tensor:
        x = F.relu_(self.bn1(self.conv1(x)))
        x = F.relu_(self.bn2(self.conv2(x)))
        if pool_type == "max":
            x = F.max_pool2d(x, kernel_size=pool_size)
        elif pool_type == "avg":
            x = F.avg_pool2d(x, kernel_size=pool_size)
        elif pool_type == "avg+max":
            x = F.avg_pool2d(x, kernel_size=pool_size) + F.max_pool2d(x, kernel_size=pool_size)
        return x


class Cnn14_DecisionLevelMax(nn.Module):
    def __init__(
        self,
        sample_rate: int = SAMPLE_RATE,
        window_size: int = WINDOW_SIZE,
        hop_size: int = HOP_SIZE,
        mel_bins: int = MEL_BINS,
        fmin: int = FMIN,
        fmax: int = FMAX,
        classes_num: int = CLASSES_NUM,
    ):
        super().__init__()
        self.interpolate_ratio = 32  # downsampled ratio

        self.spectrogram_extractor = Spectrogram(
            n_fft=window_size,
            hop_length=hop_size,
            win_length=window_size,
            window="hann",
            center=True,
            pad_mode="reflect",
            freeze_parameters=True,
        )
        self.logmel_extractor = LogmelFilterBank(
            sr=sample_rate,
            n_fft=window_size,
            n_mels=mel_bins,
            fmin=fmin,
            fmax=fmax,
            ref=1.0,
            amin=1e-10,
            top_db=None,
            freeze_parameters=True,
        )

        self.bn0 = nn.BatchNorm2d(64)
        self.conv_block1 = ConvBlock(in_channels=1, out_channels=64)
        self.conv_block2 = ConvBlock(in_channels=64, out_channels=128)
        self.conv_block3 = ConvBlock(in_channels=128, out_channels=256)
        self.conv_block4 = ConvBlock(in_channels=256, out_channels=512)
        self.conv_block5 = ConvBlock(in_channels=512, out_channels=1024)
        self.conv_block6 = ConvBlock(in_channels=1024, out_channels=2048)
        self.fc1 = nn.Linear(2048, 2048, bias=True)
        self.fc_audioset = nn.Linear(2048, classes_num, bias=True)
        self.init_weight()

    def init_weight(self) -> None:
        init_bn(self.bn0)
        init_layer(self.fc1)
        init_layer(self.fc_audioset)

    def forward(self, input: torch.Tensor) -> dict:
        """Input: (batch_size, data_length) at 32 kHz."""
        x = self.spectrogram_extractor(input)
        x = self.logmel_extractor(x)
        frames_num = x.shape[2]

        x = x.transpose(1, 3)
        x = self.bn0(x)
        x = x.transpose(1, 3)

        x = self.conv_block1(x, pool_size=(2, 2), pool_type="avg")
        x = self.conv_block2(x, pool_size=(2, 2), pool_type="avg")
        x = self.conv_block3(x, pool_size=(2, 2), pool_type="avg")
        x = self.conv_block4(x, pool_size=(2, 2), pool_type="avg")
        x = self.conv_block5(x, pool_size=(2, 2), pool_type="avg")
        x = self.conv_block6(x, pool_size=(1, 1), pool_type="avg")
        x = torch.mean(x, dim=3)

        x1 = F.max_pool1d(x, kernel_size=3, stride=1, padding=1)
        x2 = F.avg_pool1d(x, kernel_size=3, stride=1, padding=1)
        x = x1 + x2
        x = x.transpose(1, 2)
        x = F.relu_(self.fc1(x))
        segmentwise_output = torch.sigmoid(self.fc_audioset(x))
        (clipwise_output, _) = torch.max(segmentwise_output, dim=1)

        framewise_output = interpolate(segmentwise_output, self.interpolate_ratio)
        framewise_output = pad_framewise_output(framewise_output, frames_num)

        return {
            "framewise_output": framewise_output,
            "clipwise_output": clipwise_output,
        }


def load_model(checkpoint_path: str, device: torch.device) -> Cnn14_DecisionLevelMax:
    model = Cnn14_DecisionLevelMax()
    checkpoint = torch.load(checkpoint_path, map_location=device, weights_only=False)
    model.load_state_dict(checkpoint["model"], strict=False)
    model.to(device)
    model.eval()
    return model
