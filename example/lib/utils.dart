extension AverageValueCalculator on List<int> {
  double get average {
    var sum = 0;

    for (var elem in this) {
      sum += elem;
    }

    return sum / length;
  }
}